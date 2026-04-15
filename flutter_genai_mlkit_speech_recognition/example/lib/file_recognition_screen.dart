import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_genai_mlkit_speech_recognition/flutter_genai_mlkit_speech_recognition.dart';
import 'package:file_picker/file_picker.dart';

class FileRecognitionScreen extends StatefulWidget {
  const FileRecognitionScreen({super.key});

  @override
  State<FileRecognitionScreen> createState() => _FileRecognitionScreenState();
}

class _FileRecognitionScreenState extends State<FileRecognitionScreen> {
  final _plugin = FlutterGenaiMlkitSpeechRecognition();

  String? _selectedFilePath;
  String? _selectedFileName;
  String _partialText = '';
  final List<String> _finalTexts = [];
  String _aggregatedText = '';
  bool _isProcessing = false;
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
          case CompletedEvent(:final aggregatedText):
            setState(() {
              _isProcessing = false;
              _partialText = '';
              if (aggregatedText != null && aggregatedText.isNotEmpty) {
                _aggregatedText = aggregatedText;
              }
            });
            if (aggregatedText != null && aggregatedText.isNotEmpty) {
              _showSuccess('Recognition completed');
            }
          case ErrorEvent(:final message):
            setState(() {
              _isProcessing = false;
              _partialText = '';
            });
            _showError(message);
        }
      },
      onError: (error) {
        if (!mounted) return;
        setState(() {
          _isProcessing = false;
        });
      },
    );
  }

  Future<void> _pickFile() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.audio,
        allowMultiple: false,
      );

      if (result != null && result.files.single.path != null) {
        setState(() {
          _selectedFilePath = result.files.single.path;
          _selectedFileName = result.files.single.name;
          _finalTexts.clear();
          _aggregatedText = '';
          _partialText = '';
        });
      }
    } catch (e) {
      _showError('Failed to pick file: $e');
    }
  }

  Future<void> _startFileRecognition() async {
    if (_selectedFilePath == null) {
      _showError('Please select an audio file first');
      return;
    }

    try {
      await _plugin.setLocale(_selectedLocale);
      // Convert file path to content URI for Android
      await _plugin.startFileRecognition('file://$_selectedFilePath');
      setState(() {
        _isProcessing = true;
        _partialText = '';
        _finalTexts.clear();
        _aggregatedText = '';
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
        _isProcessing = false;
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
      _aggregatedText = '';
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

  void _showSuccess(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.green,
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
                  onChanged: _isProcessing
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

          // File selection
          Card(
            child: InkWell(
              onTap: _isProcessing ? null : _pickFile,
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  children: [
                    Icon(
                      _selectedFilePath != null
                          ? Icons.audio_file
                          : Icons.folder_open,
                      size: 32,
                      color: _isProcessing ? Colors.grey : Colors.blue,
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _selectedFileName ?? 'No file selected',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: _selectedFilePath != null
                                  ? FontWeight.bold
                                  : FontWeight.normal,
                            ),
                          ),
                          if (_selectedFilePath != null) ...[
                            const SizedBox(height: 4),
                            Text(
                              _selectedFilePath!,
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.grey.shade600,
                              ),
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                          ] else
                            const Text(
                              'Tap to select audio file',
                              style: TextStyle(
                                fontSize: 12,
                                color: Colors.grey,
                              ),
                            ),
                        ],
                      ),
                    ),
                    if (!_isProcessing)
                      const Icon(Icons.chevron_right, color: Colors.grey),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Control buttons
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: (_isProcessing || _selectedFilePath == null)
                      ? null
                      : _startFileRecognition,
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('Start Recognition'),
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
                  onPressed: _isProcessing ? _stopRecognition : null,
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
          if (_isProcessing)
            Card(
              color: Colors.blue.shade100,
              child: const Padding(
                padding: EdgeInsets.all(12.0),
                child: Row(
                  children: [
                    SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                    SizedBox(width: 12),
                    Text(
                      'Processing audio file...',
                      style: TextStyle(
                        color: Colors.blue,
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

          // Results section
          Row(
            children: [
              const Icon(Icons.description_outlined),
              const SizedBox(width: 8),
              const Text(
                'Recognition Results',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Aggregated result (shown first if available)
          if (_aggregatedText.isNotEmpty) ...[
            Card(
              color: Colors.green.shade50,
              child: Padding(
                padding: const EdgeInsets.all(12.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.check_circle,
                            size: 16, color: Colors.green.shade700),
                        const SizedBox(width: 4),
                        Text(
                          'Complete Transcription:',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
                            color: Colors.green.shade700,
                          ),
                        ),
                        const Spacer(),
                        IconButton(
                          icon: const Icon(Icons.copy, size: 20),
                          onPressed: () {
                            Clipboard.setData(
                              ClipboardData(text: _aggregatedText),
                            );
                            ScaffoldMessenger.of(context).showSnackBar(
                              const SnackBar(
                                content: Text('Copied to clipboard'),
                                duration: Duration(seconds: 1),
                              ),
                            );
                          },
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(_aggregatedText),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 8),
          ],

          // Individual results
          if (_finalTexts.isNotEmpty) ...[
            const Text(
              'Individual Segments:',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
          ],

          // Results list
          Expanded(
            child: (_finalTexts.isEmpty && _aggregatedText.isEmpty)
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.audio_file_outlined,
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
                          'Select an audio file and start recognition',
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
