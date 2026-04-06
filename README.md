# GenAI Speech Recognition Study (Basic)

https://developers.google.com/ml-kit/genai/speech-recognition/android?hl=ja

---

## Features

### Mic-to-TTS (マイクからTTS)
Streams audio from the device microphone to the ML Kit GenAI Speech Recognition API and displays partial/final transcription results in real time.

### File-to-TTS (ファイルからTTS)
Selects a pre-recorded audio file from device storage, decodes it to raw PCM, and submits it to the ML Kit GenAI Speech Recognition API.

---

## File-to-TTS: Supported Audio Formats

`AudioSource.fromPfd()` internally expects **raw signed 16-bit PCM** data.  
This app uses `MediaExtractor` + `MediaCodec` to decode the selected file before passing it to the API, so any format supported by Android's built-in codec can be used.

### Recommended format (best recognition accuracy)

| Property | Value |
|---|---|
| Container | WAV (RIFF) |
| Encoding | PCM signed 16-bit little-endian |
| Sample rate | **16,000 Hz (16 kHz)** |
| Channels | **Mono (1 ch)** |

> The on-device ML Kit ASR model is optimised for **16 kHz mono PCM**.  
> Files with a different sample rate or channel count will be decoded and passed as-is; recognition accuracy may be lower.

### Other formats (decoded automatically)

| Format | Extension | Notes |
|---|---|---|
| MP3 | `.mp3` | Decoded via `audio/mpeg` MediaCodec |
| AAC / M4A | `.aac`, `.m4a` | Decoded via `audio/mp4a-latm` |
| OGG Opus | `.ogg`, `.opus` | Decoded via `audio/opus` |
| OGG Vorbis | `.ogg` | Decoded via `audio/vorbis` |
| FLAC | `.flac` | Decoded via `audio/flac` |
| WAV (any encoding) | `.wav` | Header stripped; PCM written to pipe |

> ⚠️ Files at **44.1 kHz stereo** (typical music files) will decode successfully but may produce lower-quality transcription. For best results, convert to 16 kHz mono before use.

### Conversion example (ffmpeg)

```bash
ffmpeg -i input.mp3 -ar 16000 -ac 1 -sample_fmt s16 output.wav
```

---

## Requirements

- Android **12 (API 31)** or higher (ML Kit GenAI Speech Recognition requirement)
- minSdk: 28
- targetSdk / compileSdk: 36


## References 

- https://developers.google.com/ml-kit/genai/speech-recognition/android?hl=ja