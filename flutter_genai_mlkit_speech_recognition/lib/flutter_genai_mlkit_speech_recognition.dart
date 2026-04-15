import 'flutter_genai_mlkit_speech_recognition_platform_interface.dart';
import 'speech_recognition_event.dart';

export 'speech_recognition_event.dart';

/// Main API for Flutter GenAI MLKit Speech Recognition
class FlutterGenaiMlkitSpeechRecognition {
  /// Get platform version
  Future<String?> getPlatformVersion() {
    return FlutterGenaiMlkitSpeechRecognitionPlatform.instance
        .getPlatformVersion();
  }

  /// Check if the device supports speech recognition (Android 12+ required)
  ///
  /// Returns a map with:
  /// - `isSupported`: true if API level is sufficient
  /// - `apiLevel`: current device API level
  /// - `minRequired`: minimum required API level (31 for Android 12)
  Future<Map<String, dynamic>> checkApiLevel() {
    return FlutterGenaiMlkitSpeechRecognitionPlatform.instance.checkApiLevel();
  }

  /// Set the locale for speech recognition
  ///
  /// [locale] should be a valid language tag (e.g., 'ja-JP', 'en-US')
  /// Use [getSupportedLocales] to get the list of supported locales
  Future<void> setLocale(String locale) {
    return FlutterGenaiMlkitSpeechRecognitionPlatform.instance
        .setLocale(locale);
  }

  /// Start microphone-based speech recognition
  ///
  /// Requires RECORD_AUDIO permission.
  /// Listen to [recognitionEventStream] for results.
  ///
  /// Throws [PlatformException] if:
  /// - Device API level is too low (Android 12+ required)
  /// - Permission is not granted
  /// - Recognition is already in progress
  Future<void> startMicRecognition() {
    return FlutterGenaiMlkitSpeechRecognitionPlatform.instance
        .startMicRecognition();
  }

  /// Start file-based speech recognition
  ///
  /// [filePath] should be a URI to an audio file
  /// Supports formats: MP3, AAC, FLAC, OGG, WAV, etc.
  /// Listen to [recognitionEventStream] for results.
  ///
  /// Throws [PlatformException] if:
  /// - Device API level is too low (Android 12+ required)
  /// - File cannot be read or decoded
  /// - Recognition is already in progress
  Future<void> startFileRecognition(String filePath) {
    return FlutterGenaiMlkitSpeechRecognitionPlatform.instance
        .startFileRecognition(filePath);
  }

  /// Stop ongoing speech recognition
  Future<void> stopRecognition() {
    return FlutterGenaiMlkitSpeechRecognitionPlatform.instance
        .stopRecognition();
  }

  /// Get list of supported locales
  ///
  /// Returns a list of [LocaleInfo] with language tags and display names
  Future<List<LocaleInfo>> getSupportedLocales() {
    return FlutterGenaiMlkitSpeechRecognitionPlatform.instance
        .getSupportedLocales();
  }

  /// Stream of recognition events
  ///
  /// Events:
  /// - [PartialResultEvent]: Real-time partial results (may change)
  /// - [FinalResultEvent]: Confirmed recognition results
  /// - [CompletedEvent]: Recognition completed (for file recognition, includes aggregated text)
  /// - [ErrorEvent]: Recognition errors
  Stream<SpeechRecognitionEvent> get recognitionEventStream {
    return FlutterGenaiMlkitSpeechRecognitionPlatform.instance
        .recognitionEventStream;
  }
}
