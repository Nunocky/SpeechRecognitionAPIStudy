import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_genai_mlkit_speech_recognition/flutter_genai_mlkit_speech_recognition.dart';
import 'package:flutter_genai_mlkit_speech_recognition/flutter_genai_mlkit_speech_recognition_platform_interface.dart';
import 'package:flutter_genai_mlkit_speech_recognition/flutter_genai_mlkit_speech_recognition_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockFlutterGenaiMlkitSpeechRecognitionPlatform
    with MockPlatformInterfaceMixin
    implements FlutterGenaiMlkitSpeechRecognitionPlatform {
  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final FlutterGenaiMlkitSpeechRecognitionPlatform initialPlatform = FlutterGenaiMlkitSpeechRecognitionPlatform.instance;

  test('$MethodChannelFlutterGenaiMlkitSpeechRecognition is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelFlutterGenaiMlkitSpeechRecognition>());
  });

  test('getPlatformVersion', () async {
    FlutterGenaiMlkitSpeechRecognition flutterGenaiMlkitSpeechRecognitionPlugin = FlutterGenaiMlkitSpeechRecognition();
    MockFlutterGenaiMlkitSpeechRecognitionPlatform fakePlatform = MockFlutterGenaiMlkitSpeechRecognitionPlatform();
    FlutterGenaiMlkitSpeechRecognitionPlatform.instance = fakePlatform;

    expect(await flutterGenaiMlkitSpeechRecognitionPlugin.getPlatformVersion(), '42');
  });
}
