import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_genai_mlkit_speech_recognition/flutter_genai_mlkit_speech_recognition.dart';

import 'mic_recognition_screen.dart';
import 'file_recognition_screen.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Speech Recognition Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const SpeechRecognitionDemo(),
    );
  }
}

class SpeechRecognitionDemo extends StatefulWidget {
  const SpeechRecognitionDemo({super.key});

  @override
  State<SpeechRecognitionDemo> createState() => _SpeechRecognitionDemoState();
}

class _SpeechRecognitionDemoState extends State<SpeechRecognitionDemo>
    with SingleTickerProviderStateMixin {
  final _plugin = FlutterGenaiMlkitSpeechRecognition();
  late TabController _tabController;

  String _platformVersion = 'Unknown';
  bool _isSupported = false;
  String _statusMessage = '';

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _initPlatform();
  }

  Future<void> _initPlatform() async {
    try {
      final version = await _plugin.getPlatformVersion() ?? 'Unknown';
      final apiLevelInfo = await _plugin.checkApiLevel();
      final isSupported = apiLevelInfo['isSupported'] as bool? ?? false;
      final apiLevel = apiLevelInfo['apiLevel'] as int? ?? 0;
      final minRequired = apiLevelInfo['minRequired'] as int? ?? 0;

      if (!mounted) return;

      setState(() {
        _platformVersion = version;
        _isSupported = isSupported;

        if (isSupported) {
          _statusMessage = 'Ready (API $apiLevel)';
        } else {
          _statusMessage =
              'Not supported (API $apiLevel, requires $minRequired+)';
        }
      });

      if (!isSupported) {
        _showError(
            'This device does not support ML Kit Speech Recognition.\nAndroid 12 (API 31) or higher is required.');
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _statusMessage = 'Error: $e';
      });
    }
  }

  void _showError(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.red,
        duration: const Duration(seconds: 5),
      ),
    );
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Speech Recognition Demo'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(
              icon: Icon(Icons.mic),
              text: 'Microphone',
            ),
            Tab(
              icon: Icon(Icons.audio_file),
              text: 'File',
            ),
          ],
        ),
      ),
      body: Column(
        children: [
          // Platform info banner
          Container(
            width: double.infinity,
            color: _isSupported
                ? Colors.green.shade100
                : Colors.orange.shade100,
            padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
            child: Row(
              children: [
                Icon(
                  _isSupported ? Icons.check_circle : Icons.warning,
                  color: _isSupported ? Colors.green.shade700 : Colors.orange.shade700,
                  size: 20,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '$_platformVersion • $_statusMessage',
                    style: TextStyle(
                      fontSize: 12,
                      color: _isSupported
                          ? Colors.green.shade700
                          : Colors.orange.shade700,
                    ),
                  ),
                ),
              ],
            ),
          ),
          // Tab views
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: const [
                MicRecognitionScreen(),
                FileRecognitionScreen(),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
