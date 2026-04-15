/// Base class for speech recognition events
sealed class SpeechRecognitionEvent {
  const SpeechRecognitionEvent();

  factory SpeechRecognitionEvent.fromMap(Map<dynamic, dynamic> map) {
    final type = map['type'] as String?;

    switch (type) {
      case 'partial':
        return PartialResultEvent(map['text'] as String? ?? '');
      case 'final':
        return FinalResultEvent(map['text'] as String? ?? '');
      case 'completed':
        return CompletedEvent(
          aggregatedText: map['aggregatedText'] as String?,
        );
      case 'error':
        return ErrorEvent(map['message'] as String? ?? 'Unknown error');
      default:
        return ErrorEvent('Unknown event type: $type');
    }
  }
}

/// Partial recognition result (real-time, may change)
class PartialResultEvent extends SpeechRecognitionEvent {
  final String text;

  const PartialResultEvent(this.text);

  @override
  String toString() => 'PartialResultEvent(text: $text)';

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is PartialResultEvent && other.text == text;
  }

  @override
  int get hashCode => text.hashCode;
}

/// Final recognition result (confirmed)
class FinalResultEvent extends SpeechRecognitionEvent {
  final String text;

  const FinalResultEvent(this.text);

  @override
  String toString() => 'FinalResultEvent(text: $text)';

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is FinalResultEvent && other.text == text;
  }

  @override
  int get hashCode => text.hashCode;
}

/// Recognition completed
class CompletedEvent extends SpeechRecognitionEvent {
  /// For file recognition, contains all recognized text aggregated
  final String? aggregatedText;

  const CompletedEvent({this.aggregatedText});

  @override
  String toString() => 'CompletedEvent(aggregatedText: $aggregatedText)';

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is CompletedEvent && other.aggregatedText == aggregatedText;
  }

  @override
  int get hashCode => aggregatedText.hashCode;
}

/// Recognition error
class ErrorEvent extends SpeechRecognitionEvent {
  final String message;

  const ErrorEvent(this.message);

  @override
  String toString() => 'ErrorEvent(message: $message)';

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is ErrorEvent && other.message == message;
  }

  @override
  int get hashCode => message.hashCode;
}

/// Locale information
class LocaleInfo {
  final String tag;
  final String displayName;

  const LocaleInfo({
    required this.tag,
    required this.displayName,
  });

  factory LocaleInfo.fromMap(Map<dynamic, dynamic> map) {
    return LocaleInfo(
      tag: map['tag'] as String,
      displayName: map['displayName'] as String,
    );
  }

  @override
  String toString() => 'LocaleInfo(tag: $tag, displayName: $displayName)';

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is LocaleInfo &&
        other.tag == tag &&
        other.displayName == displayName;
  }

  @override
  int get hashCode => tag.hashCode ^ displayName.hashCode;
}
