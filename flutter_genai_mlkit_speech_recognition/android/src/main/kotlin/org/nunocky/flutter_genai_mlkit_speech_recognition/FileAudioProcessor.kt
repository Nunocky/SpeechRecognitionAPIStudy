package org.nunocky.flutter_genai_mlkit_speech_recognition

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.delay

private const val AUDIO_TAG = "FileAudioProcessor"
private const val TARGET_SAMPLE_RATE = 16000
private const val STREAM_BYTES_PER_SECOND = 32000L
private const val STREAM_SPEED_FACTOR = 4L
private const val STREAM_CHUNK_BYTES = 6400

private fun ByteArray.getShortLE(offset: Int): Int =
    ((this[offset + 1].toInt() shl 8) or (this[offset].toInt() and 0xFF)).toShort().toInt()

private fun ByteArray.getFloatLE(offset: Int): Float {
    val bits =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    return Float.fromBits(bits)
}

private fun pcmEncodingLabel(pcmEncoding: Int): String = when (pcmEncoding) {
    AudioFormat.ENCODING_PCM_16BIT -> "PCM_16BIT"
    AudioFormat.ENCODING_PCM_8BIT -> "PCM_8BIT"
    AudioFormat.ENCODING_PCM_FLOAT -> "PCM_FLOAT"
    else -> "UNKNOWN($pcmEncoding)"
}

private fun convertToTargetPcm(
    input: ByteArray,
    inputSampleRate: Int,
    channelCount: Int,
    pcmEncoding: Int
): ByteArray {
    val bytesPerSample = when (pcmEncoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }
    val frameSize = channelCount * bytesPerSample
    if (frameSize <= 0 || input.size < frameSize) return ByteArray(0)

    val inputFrames = input.size / frameSize

    val mono = ShortArray(inputFrames) { frame ->
        var sum = 0
        for (ch in 0 until channelCount) {
            val sampleOffset = frame * frameSize + ch * bytesPerSample
            val sample = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_8BIT -> {
                    // 8-bit PCM in Android is unsigned [0, 255].
                    ((input[sampleOffset].toInt() and 0xFF) - 128) shl 8
                }

                AudioFormat.ENCODING_PCM_FLOAT -> {
                    val f = input.getFloatLE(sampleOffset).coerceIn(-1f, 1f)
                    (f * 32767f).toInt()
                }

                else -> {
                    input.getShortLE(sampleOffset)
                }
            }
            sum += sample
        }
        (sum / channelCount).coerceIn(-32768, 32767).toShort()
    }

    if (inputSampleRate == TARGET_SAMPLE_RATE) {
        val out = ByteArray(mono.size * 2)
        mono.forEachIndexed { i, s ->
            out[i * 2] = (s.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
        }
        return out
    }

    val ratio = inputSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
    val outputFrames = (inputFrames / ratio).toInt()
    val out = ByteArray(outputFrames * 2)
    for (i in 0 until outputFrames) {
        val srcPos = i * ratio
        val srcIdx = srcPos.toInt().coerceAtMost(mono.size - 1)
        val frac = srcPos - srcIdx
        val s0 = mono[srcIdx].toInt()
        val s1 = if (srcIdx + 1 < mono.size) mono[srcIdx + 1].toInt() else s0
        val s = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767)
        out[i * 2] = (s and 0xFF).toByte()
        out[i * 2 + 1] = (s shr 8 and 0xFF).toByte()
    }
    return out
}

internal fun createTempPcmFile(
    context: Context,
    uri: Uri
): File {
    val tempFile = File.createTempFile("asr_input_", ".pcm", context.cacheDir)

    var extractor: MediaExtractor? = null
    var codec: MediaCodec? = null
    try {
        extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)

        var audioTrackIdx = -1
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackIdx = i
                break
            }
        }
        if (audioTrackIdx < 0) {
            error("No audio track found in selected file")
        }

        extractor.selectTrack(audioTrackIdx)
        val format = extractor.getTrackFormat(audioTrackIdx)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        var outSampleRate =
            runCatching { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(44100)
        var outChannelCount =
            runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(1)
        var outPcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmBuffer = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIdx = codec.dequeueInputBuffer(10_000L)
                if (inputIdx >= 0) {
                    val inputBuf = codec.getInputBuffer(inputIdx)!!
                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIdx, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            when {
                outputIdx >= 0 -> {
                    val outputBuf = codec.getOutputBuffer(outputIdx)!!
                    if (bufferInfo.size > 0) {
                        outputBuf.position(bufferInfo.offset)
                        outputBuf.limit(bufferInfo.offset + bufferInfo.size)

                        val rawPcm = ByteArray(bufferInfo.size)
                        outputBuf.get(rawPcm)
                        val converted = convertToTargetPcm(
                            rawPcm,
                            outSampleRate,
                            outChannelCount,
                            outPcmEncoding
                        )
                        pcmBuffer.write(converted)
                    }
                    codec.releaseOutputBuffer(outputIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }

                outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFmt = codec.outputFormat
                    outSampleRate =
                        runCatching { newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }
                            .getOrDefault(outSampleRate)
                    outChannelCount =
                        runCatching { newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                            .getOrDefault(outChannelCount)
                    outPcmEncoding = runCatching {
                        newFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    }.getOrDefault(outPcmEncoding)
                    Log.d(
                        AUDIO_TAG,
                        "createTempPcmFile: output format updated -> sampleRate=$outSampleRate channels=$outChannelCount pcmEncoding=${pcmEncodingLabel(outPcmEncoding)}"
                    )
                }
            }
        }

        val pcmData = pcmBuffer.toByteArray()
        if (pcmData.isEmpty()) {
            error("Decoded PCM is empty")
        }

        FileOutputStream(tempFile).use { out ->
            out.write(pcmData)
            out.flush()
        }

        val approxDurationMs = pcmData.size / 32
        Log.d(
            AUDIO_TAG,
            "createTempPcmFile: complete path=${tempFile.absolutePath} bytes=${pcmData.size} durationMs~=$approxDurationMs"
        )
        return tempFile
    } catch (e: Exception) {
        runCatching { tempFile.delete() }
        Log.e(AUDIO_TAG, "createTempPcmFile: error - ${e.message}", e)
        throw e
    } finally {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { extractor?.release() }
    }
}

internal suspend fun streamPcmFileToPipe(
    pcmFile: File,
    writeFd: ParcelFileDescriptor
) {
    try {
        FileInputStream(pcmFile).use { input ->
            FileOutputStream(writeFd.fileDescriptor).use { output ->
                // 200ms chunk at 16kHz mono 16-bit = 6400 bytes.
                val buffer = ByteArray(STREAM_CHUNK_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    // Keep paced delivery but at faster-than-realtime speed.
                    val millis = (read * 1000L) / (STREAM_BYTES_PER_SECOND * STREAM_SPEED_FACTOR)
                    if (millis > 0) delay(millis)
                }
                output.flush()
            }
        }
        Log.d(
            AUDIO_TAG,
            "streamPcmFileToPipe: streamed bytes=${pcmFile.length()} speedFactor=${STREAM_SPEED_FACTOR}x"
        )
    } catch (e: Exception) {
        Log.e(AUDIO_TAG, "streamPcmFileToPipe: error - ${e.message}", e)
        throw e
    } finally {
        runCatching { writeFd.close() }
    }
}
