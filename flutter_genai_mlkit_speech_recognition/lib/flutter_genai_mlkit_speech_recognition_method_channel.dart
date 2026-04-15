import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_genai_mlkit_speech_recognition_platform_interface.dart';
import 'speech_recognition_event.dart';

/// An implementation of [FlutterGenaiMlkitSpeechRecognitionPlatform] that uses method channels.
class MethodChannelFlutterGenaiMlkitSpeechRecognition
    extends FlutterGenaiMlkitSpeechRecognitionPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel(
      'flutter_genai_mlkit_speech_recognition/methods');

  /// The event channel used to receive recognition events.
  @visibleForTesting
  final eventChannel = const EventChannel(
      'flutter_genai_mlkit_speech_recognition/events');

  Stream<SpeechRecognitionEvent>? _eventStream;

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  @override
  Future<Map<String, dynamic>> checkApiLevel() async {
    final result = await methodChannel.invokeMethod<Map>('checkApiLevel');
    return Map<String, dynamic>.from(result ?? {});
  }

  @override
  Future<void> setLocale(String locale) async {
    await methodChannel.invokeMethod<void>('setLocale', {'locale': locale});
  }

  @override
  Future<void> startMicRecognition() async {
    await methodChannel.invokeMethod<void>('startMicRecognition');
  }

  @override
  Future<void> startFileRecognition(String filePath) async {
    await methodChannel.invokeMethod<void>(
      'startFileRecognition',
      {'filePath': filePath},
    );
  }

  @override
  Future<void> stopRecognition() async {
    await methodChannel.invokeMethod<void>('stopRecognition');
  }

  @override
  Future<List<LocaleInfo>> getSupportedLocales() async {
    final result = await methodChannel.invokeMethod<List>('getSupportedLocales');
    if (result == null) return [];

    return result
        .map((item) => LocaleInfo.fromMap(Map<dynamic, dynamic>.from(item)))
        .toList();
  }

  @override
  Stream<SpeechRecognitionEvent> get recognitionEventStream {
    _eventStream ??= eventChannel
        .receiveBroadcastStream()
        .map((event) => SpeechRecognitionEvent.fromMap(
              Map<dynamic, dynamic>.from(event as Map),
            ));
    return _eventStream!;
  }
}
