package org.nunocky.flutter_genai_mlkit_speech_recognition

import android.os.Build
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** FlutterGenaiMlkitSpeechRecognitionPlugin */
class FlutterGenaiMlkitSpeechRecognitionPlugin :
    FlutterPlugin,
    MethodCallHandler,
    EventChannel.StreamHandler {

    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var recognitionHandler: SpeechRecognitionHandler? = null
    private var coroutineScope: CoroutineScope? = null
    private var flutterPluginBinding: FlutterPlugin.FlutterPluginBinding? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        flutterPluginBinding = binding

        methodChannel = MethodChannel(
            binding.binaryMessenger,
            "flutter_genai_mlkit_speech_recognition/methods"
        )
        methodChannel.setMethodCallHandler(this)

        eventChannel = EventChannel(
            binding.binaryMessenger,
            "flutter_genai_mlkit_speech_recognition/events"
        )
        eventChannel.setStreamHandler(this)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        coroutineScope = scope
        recognitionHandler = SpeechRecognitionHandler(
            binding.applicationContext,
            scope
        )
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${Build.VERSION.RELEASE}")
            }
            "checkApiLevel" -> {
                val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                result.success(mapOf(
                    "isSupported" to isSupported,
                    "apiLevel" to Build.VERSION.SDK_INT,
                    "minRequired" to Build.VERSION_CODES.S
                ))
            }
            "setLocale" -> {
                val locale = call.argument<String>("locale")
                if (locale != null) {
                    recognitionHandler?.setLocale(locale)
                    result.success(null)
                } else {
                    result.error("INVALID_ARGUMENT", "locale is required", null)
                }
            }
            "startMicRecognition" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    result.error(
                        "UNSUPPORTED_VERSION",
                        "Android 12 (API 31) or higher is required",
                        null
                    )
                    return
                }
                try {
                    recognitionHandler?.startMicRecognition()
                    result.success(null)
                } catch (e: Exception) {
                    result.error("START_FAILED", e.message, null)
                }
            }
            "startFileRecognition" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    result.error(
                        "UNSUPPORTED_VERSION",
                        "Android 12 (API 31) or higher is required",
                        null
                    )
                    return
                }
                val filePath = call.argument<String>("filePath")
                if (filePath != null) {
                    try {
                        recognitionHandler?.startFileRecognition(filePath)
                        result.success(null)
                    } catch (e: Exception) {
                        result.error("START_FAILED", e.message, null)
                    }
                } else {
                    result.error("INVALID_ARGUMENT", "filePath is required", null)
                }
            }
            "stopRecognition" -> {
                recognitionHandler?.stopRecognition()
                result.success(null)
            }
            "getSupportedLocales" -> {
                val locales = SupportedSpeechLocales.options.map {
                    mapOf("tag" to it.tag, "displayName" to it.displayName)
                }
                result.success(locales)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        recognitionHandler?.setEventSink(events)
    }

    override fun onCancel(arguments: Any?) {
        recognitionHandler?.setEventSink(null)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
        recognitionHandler?.cleanup()
        recognitionHandler = null
        coroutineScope?.cancel()
        coroutineScope = null
        flutterPluginBinding = null
    }
}
