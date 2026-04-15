import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_genai_mlkit_speech_recognition_method_channel.dart';
import 'speech_recognition_event.dart';

abstract class FlutterGenaiMlkitSpeechRecognitionPlatform extends PlatformInterface {
  /// Constructs a FlutterGenaiMlkitSpeechRecognitionPlatform.
  FlutterGenaiMlkitSpeechRecognitionPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterGenaiMlkitSpeechRecognitionPlatform _instance =
      MethodChannelFlutterGenaiMlkitSpeechRecognition();

  /// The default instance of [FlutterGenaiMlkitSpeechRecognitionPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterGenaiMlkitSpeechRecognition].
  static FlutterGenaiMlkitSpeechRecognitionPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterGenaiMlkitSpeechRecognitionPlatform] when
  /// they register themselves.
  static set instance(FlutterGenaiMlkitSpeechRecognitionPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  /// Check if the device supports speech recognition (API level check)
  Future<Map<String, dynamic>> checkApiLevel() {
    throw UnimplementedError('checkApiLevel() has not been implemented.');
  }

  /// Set the locale for speech recognition
  Future<void> setLocale(String locale) {
    throw UnimplementedError('setLocale() has not been implemented.');
  }

  /// Start microphone-based speech recognition
  Future<void> startMicRecognition() {
    throw UnimplementedError('startMicRecognition() has not been implemented.');
  }

  /// Start file-based speech recognition
  Future<void> startFileRecognition(String filePath) {
    throw UnimplementedError(
        'startFileRecognition() has not been implemented.');
  }

  /// Stop ongoing speech recognition
  Future<void> stopRecognition() {
    throw UnimplementedError('stopRecognition() has not been implemented.');
  }

  /// Get list of supported locales
  Future<List<LocaleInfo>> getSupportedLocales() {
    throw UnimplementedError('getSupportedLocales() has not been implemented.');
  }

  /// Stream of recognition events
  Stream<SpeechRecognitionEvent> get recognitionEventStream {
    throw UnimplementedError('recognitionEventStream has not been implemented.');
  }
}
