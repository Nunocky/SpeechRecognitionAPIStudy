import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_genai_mlkit_speech_recognition/flutter_genai_mlkit_speech_recognition.dart';
import 'package:permission_handler/permission_handler.dart';

class MicRecognitionScreen extends StatefulWidget {
  const MicRecognitionScreen({super.key});

  @override
  State<MicRecognitionScreen> createState() => _MicRecognitionScreenState();
}

class _MicRecognitionScreenState extends State<MicRecognitionScreen> {
  final _plugin = FlutterGenaiMlkitSpeechRecognition();

  String _partialText = '';
  final List<String> _finalTexts = [];
  bool _isListening = false;
  String _selectedLocale = 'ja-JP';
  List<LocaleInfo> _supportedLocales = [];
  StreamSubscription<SpeechRecognitionEvent>? _eventSubscription;

  @override
  void initState() {
    super.initState();
    _initPlatform();
    _listenToEvents();
  }

  Future<void> _initPlatform() async {
    try {
      final locales = await _plugin.getSupportedLocales();
      if (!mounted) return;

      setState(() {
        _supportedLocales = locales;
      });

      if (locales.isNotEmpty) {
        await _plugin.setLocale(_selectedLocale);
      }
    } catch (e) {
      if (!mounted) return;
      _showError('Failed to initialize: $e');
    }
  }

  void _listenToEvents() {
    _eventSubscription = _plugin.recognitionEventStream.listen(
      (event) {
        if (!mounted) return;

        switch (event) {
          case PartialResultEvent(:final text):
            setState(() {
              _partialText = text;
            });
          case FinalResultEvent(:final text):
            setState(() {
              _finalTexts.add(text);
              _partialText = '';
            });
          case CompletedEvent():
            setState(() {
              _isListening = false;
              _partialText = '';
            });
          case ErrorEvent(:final message):
            setState(() {
              _isListening = false;
              _partialText = '';
            });
            _showError(message);
        }
      },
      onError: (error) {
        if (!mounted) return;
        setState(() {
          _isListening = false;
        });
      },
    );
  }

  Future<void> _requestPermission() async {
    final status = await Permission.microphone.request();
    if (status.isDenied || status.isPermanentlyDenied) {
      if (!mounted) return;
      _showError('Microphone permission is required for speech recognition');
    }
  }

  Future<void> _startMicRecognition() async {
    // Check permission
    final status = await Permission.microphone.status;
    if (!status.isGranted) {
      await _requestPermission();
      final newStatus = await Permission.microphone.status;
      if (!newStatus.isGranted) {
        return;
      }
    }

    try {
      await _plugin.setLocale(_selectedLocale);
      await _plugin.startMicRecognition();
      setState(() {
        _isListening = true;
        _partialText = '';
      });
    } on PlatformException catch (e) {
      if (!mounted) return;
      _showError(e.message ?? 'Unknown error');
    }
  }

  Future<void> _stopRecognition() async {
    try {
      await _plugin.stopRecognition();
      setState(() {
        _isListening = false;
        _partialText = '';
      });
    } catch (e) {
      if (!mounted) return;
      _showError('Error stopping recognition: $e');
    }
  }

  void _clearResults() {
    setState(() {
      _finalTexts.clear();
      _partialText = '';
    });
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.red,
      ),
    );
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Locale selector
          if (_supportedLocales.isNotEmpty) ...[
            Card(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12.0),
                child: DropdownButton<String>(
                  value: _selectedLocale,
                  isExpanded: true,
                  underline: const SizedBox.shrink(),
                  items: _supportedLocales.map((locale) {
                    return DropdownMenuItem(
                      value: locale.tag,
                      child: Text(locale.displayName),
                    );
                  }).toList(),
                  onChanged: _isListening
                      ? null
                      : (value) {
                          if (value != null) {
                            setState(() {
                              _selectedLocale = value;
                            });
                          }
                        },
                ),
              ),
            ),
            const SizedBox(height: 16),
          ],

          // Control buttons
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _isListening ? null : _startMicRecognition,
                  icon: const Icon(Icons.mic),
                  label: const Text('Start'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _isListening ? _stopRecognition : null,
                  icon: const Icon(Icons.stop),
                  label: const Text('Stop'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ),
              const SizedBox(width: 8),
              IconButton(
                onPressed: _clearResults,
                icon: const Icon(Icons.clear),
                tooltip: 'Clear results',
              ),
            ],
          ),
          const SizedBox(height: 16),

          // Status indicator
          if (_isListening)
            Card(
              color: Colors.green.shade100,
              child: const Padding(
                padding: EdgeInsets.all(12.0),
                child: Row(
                  children: [
                    Icon(Icons.mic, color: Colors.green),
                    SizedBox(width: 8),
                    Text(
                      'Listening...',
                      style: TextStyle(
                        color: Colors.green,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
              ),
            ),

          // Partial text
          if (_partialText.isNotEmpty) ...[
            const SizedBox(height: 8),
            Card(
              color: Colors.blue.shade50,
              child: Padding(
                padding: const EdgeInsets.all(12.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.pending, size: 16, color: Colors.blue.shade700),
                        const SizedBox(width: 4),
                        Text(
                          'Partial Result:',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 12,
                            color: Colors.blue.shade700,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Text(_partialText),
                  ],
                ),
              ),
            ),
          ],

          const SizedBox(height: 16),

          // Results header
          Row(
            children: [
              const Icon(Icons.chat_bubble_outline),
              const SizedBox(width: 8),
              const Text(
                'Recognition Results',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const Spacer(),
              if (_finalTexts.isNotEmpty)
                Text(
                  '${_finalTexts.length} items',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey.shade600,
                  ),
                ),
            ],
          ),
          const SizedBox(height: 8),

          // Results list
          Expanded(
            child: _finalTexts.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.mic_none,
                          size: 64,
                          color: Colors.grey.shade400,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'No results yet',
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.grey.shade600,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Press "Start" to begin recognition',
                          style: TextStyle(
                            fontSize: 14,
                            color: Colors.grey.shade500,
                          ),
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    itemCount: _finalTexts.length,
                    itemBuilder: (context, index) {
                      return Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: Colors.blue,
                            child: Text(
                              '${index + 1}',
                              style: const TextStyle(color: Colors.white),
                            ),
                          ),
                          title: Text(_finalTexts[index]),
                          trailing: IconButton(
                            icon: const Icon(Icons.copy, size: 20),
                            onPressed: () {
                              Clipboard.setData(
                                ClipboardData(text: _finalTexts[index]),
                              );
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(
                                  content: Text('Copied to clipboard'),
                                  duration: Duration(seconds: 1),
                                ),
                              );
                            },
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
