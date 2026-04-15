# flutter_genai_mlkit_speech_recognition

A Flutter plugin for Google ML Kit GenAI Speech Recognition API on Android.

This plugin provides Flutter bindings for the ML Kit GenAI Speech Recognition API, enabling both microphone-based real-time speech recognition and file-based audio transcription.

## Features

- 🎤 **Microphone Recognition**: Real-time speech-to-text from microphone input
- 📁 **File Recognition**: Transcribe audio files (MP3, AAC, FLAC, OGG, WAV, etc.)
- 🌍 **Multi-language Support**: 15 languages including English, Japanese, Chinese, Spanish, etc.
- 🔄 **Streaming Results**: Receive partial and final recognition results in real-time
- 📊 **Event-based API**: Stream-based architecture for easy integration

## Requirements

- **Android 12 (API level 31) or higher**
- Flutter SDK
- For microphone recognition: `RECORD_AUDIO` permission

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  flutter_genai_mlkit_speech_recognition: ^0.0.1
```

## Platform Setup

### Android

Add the following permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

For microphone recognition, you'll need to request the `RECORD_AUDIO` permission at runtime. Consider using the [permission_handler](https://pub.dev/packages/permission_handler) package.

## Usage

### Basic Setup

```dart
import 'package:flutter_genai_mlkit_speech_recognition/flutter_genai_mlkit_speech_recognition.dart';

final plugin = FlutterGenaiMlkitSpeechRecognition();
```

### Check Device Support

```dart
final apiLevelInfo = await plugin.checkApiLevel();
final isSupported = apiLevelInfo['isSupported'] as bool;

if (!isSupported) {
  print('Device does not support speech recognition');
}
```

### Set Locale

```dart
// Get supported locales
final locales = await plugin.getSupportedLocales();

// Set locale for recognition
await plugin.setLocale('ja-JP');
```

### Microphone Recognition

```dart
// Listen to events
plugin.recognitionEventStream.listen((event) {
  switch (event) {
    case PartialResultEvent(:final text):
      print('Partial: $text');
    case FinalResultEvent(:final text):
      print('Final: $text');
    case CompletedEvent():
      print('Recognition completed');
    case ErrorEvent(:final message):
      print('Error: $message');
  }
});

// Start recognition
await plugin.startMicRecognition();

// Stop recognition
await plugin.stopRecognition();
```

### File Recognition

```dart
// Listen to events
plugin.recognitionEventStream.listen((event) {
  switch (event) {
    case PartialResultEvent(:final text):
      print('Partial: $text');
    case FinalResultEvent(:final text):
      print('Final: $text');
    case CompletedEvent(:final aggregatedText):
      print('Completed with aggregated text: $aggregatedText');
    case ErrorEvent(:final message):
      print('Error: $message');
  }
});

// Start file recognition (use content:// URI for Android)
await plugin.startFileRecognition('content://path/to/audio/file');

// Stop recognition
await plugin.stopRecognition();
```

## Supported Locales

The following languages are supported:

- English (en-US)
- 日本語 (ja-JP)
- 中文 简体 (cmn-Hans-CN)
- 中文 繁體 (cmn-Hant-TW)
- 한국어 (ko-KR)
- Français (fr-FR)
- Deutsch (de-DE)
- Español (es-ES)
- Italiano (it-IT)
- Português (pt-BR)
- Русский (ru-RU)
- हिन्दी (hi-IN)
- Türkçe (tr-TR)
- Polski (pl-PL)
- Tiếng Việt (vi-VN)

## Events

### SpeechRecognitionEvent

Base class for all recognition events:

- **PartialResultEvent**: Real-time partial results (may change as recognition continues)
- **FinalResultEvent**: Confirmed recognition results
- **CompletedEvent**: Recognition completed
  - For file recognition, includes `aggregatedText` with all recognized text
- **ErrorEvent**: Recognition errors

## Example

See the [example](example) directory for a complete demo app.

To run the example:

```bash
cd example
flutter run
```

## Supported Audio Formats (File Recognition)

The plugin supports various audio formats through Android's MediaCodec:

- MP3
- AAC
- FLAC
- OGG Vorbis
- WAV
- And more formats supported by Android MediaCodec

Audio files are automatically decoded and converted to 16kHz mono 16-bit PCM for recognition.

## Limitations

- **Android Only**: This plugin currently only supports Android
- **API Level**: Requires Android 12 (API level 31) or higher
- **Network**: ML Kit may download language models on first use (requires internet connection)

## Architecture

This plugin uses:
- **Method Channel**: For control operations (start/stop recognition, set locale)
- **Event Channel**: For streaming recognition results
- **ML Kit GenAI Speech Recognition API**: Google's on-device speech recognition
- **MediaExtractor/MediaCodec**: For audio file decoding

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

Based on the Android implementation from [SpeechRecognitionAPIStudy](https://github.com/nunocky/SpeechRecognitionAPIStudy)

