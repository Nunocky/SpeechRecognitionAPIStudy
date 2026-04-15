# Speech Recognition API Study - Codebase Guide

A comprehensive Android study application for evaluating on-device speech recognition with the ML Kit GenAI Speech Recognition API, featuring both microphone-to-text and file-to-text transcription modes.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [Code Organization](#code-organization)
5. [Key Components](#key-components)
6. [Development Workflow](#development-workflow)
7. [Common Commands](#common-commands)
8. [Project Configuration](#project-configuration)
9. [Special Project Management](#special-project-management)

---

## Project Overview

### Purpose

This is a study/research project demonstrating integration of Google ML Kit's GenAI Speech Recognition API with Android Jetpack Compose. The app showcases two primary features:

1. **Mic-to-TTS (Real-time Microphone Input)**: Streams audio from the device microphone directly to the ML Kit API, displaying partial and final transcriptions in real-time
2. **File-to-TTS (Pre-recorded Audio Processing)**: Allows users to select audio files from device storage, decode them to PCM format, and process them through the speech recognition API

### Target Use Cases

- Prototyping voice input UX in Android applications
- Learning ML Kit speech recognition integration with Jetpack Compose
- Verifying API behavior across response event types (partial, final, completed, error)
- Baseline implementation for advanced voice features (post-processing, command routing, conversation UX)

### Key Features

- Real-time partial and final transcription display in chat-style timeline
- Multi-language locale support (15+ languages including English, Japanese, Chinese, Spanish, etc.)
- File audio format support (MP3, AAC, M4A, OGG, FLAC, WAV) with automatic PCM decoding
- Microphone permission handling with runtime checks
- Android 12+ (API 31+) compatibility with graceful API level gating
- Model download management with progress tracking

---

## Architecture

### Design Philosophy

Lightweight, Compose-first Android architecture optimized for prototyping and learning:

- **UI-Driven State Management**: State stored in ViewModels using Kotlin Flow/StateFlow
- **Direct API Integration**: ML Kit speech recognition integrated directly in screen/ViewModel layers
- **Layered by Feature**: Organized into feature-specific packages (mic, file, main screens)
- **Single-Module Structure**: All code in `/app` module with no complex multi-module setup
- **Lifecycle-Aware**: Proper cleanup and resource management through Android lifecycle

### High-Level Flow

```
MainActivity (Compose Container)
    └── AppNavHost (Navigation Router)
        ├── StartupMenuScreen
        │   ├── Mic-to-TTS Mode Selection
        │   └── File-to-TTS Mode Selection
        │
        ├── SpeechRecognitionScreen (Mic Mode)
        │   ├── SpeechRecognitionViewModel
        │   │   ├── AudioSource.fromMic()
        │   │   ├── SpeechRecognizer.startRecognition()
        │   │   └── Flow<SpeechRecognizerResponse>
        │   └── ChatBubble UI (displays transcriptions)
        │
        └── FileToTtsScreen (File Mode)
            ├── FileToTtsViewModel
            │   ├── File Selection & Decoding
            │   ├── FileAudioProcessor (MediaExtractor + MediaCodec)
            │   ├── PCM Conversion & Streaming
            │   └── SpeechRecognizer.startRecognition()
            └── ChatBubble UI + Media Controls
```

### State Management Pattern

Each screen has:

1. **UiState** (immutable data class)
   - Holds current UI state (listening/processing flags, text, messages)
   - Managed via `MutableStateFlow<UiState>` in ViewModel
   - Collected by UI layer and recomposed on changes

2. **UiEvent** (sealed class)
   - One-time events (errors, unsupported version)
   - Emitted via `MutableSharedFlow<UiEvent>`
   - Collected by UI layer for toast/dialog display

3. **Recognition Lifecycle**
   - Tracked via `recognitionJob: Job?` to prevent duplicate collectors
   - Cancellation treated as expected control flow (not surfaced as error)
   - Proper cleanup in `finally` blocks

---

## Technology Stack

### Languages & Runtimes

| Component | Version |
|-----------|---------|
| **Kotlin** | 2.2.10 (JVM target 11) |
| **Java** | 11 (source/target compatibility) |
| **Android API** | minSdk: 28, targetSdk: 36, compileSdk: 36 |

### Core Android Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| **AndroidX Core KTX** | 1.18.0 | Kotlin extensions for Android APIs |
| **AndroidX Lifecycle Runtime KTX** | 2.10.0 | Lifecycle-aware coroutines |
| **AndroidX Activity Compose** | 1.13.0 | Compose activity hosting |
| **Jetpack Compose** | 2024.09.00 (BOM) | UI framework |
| **Material 3** | Latest (via BOM) | Material Design system |
| **Navigation Compose** | 2.9.0 | Composable navigation routing |
| **Lifecycle ViewModel Compose** | 2.10.0 | ViewModel integration with Compose |

### Speech Recognition & Media

| Library | Version | Purpose |
|---------|---------|---------|
| **ML Kit GenAI Speech Recognition** | 1.0.0-alpha1 | On-device speech-to-text API |
| **Android Media APIs** | Platform | AudioSource, MediaExtractor, MediaCodec, MediaPlayer |

### Build & Development

| Tool | Version | Purpose |
|------|---------|---------|
| **Android Gradle Plugin** | 9.1.0 | Build orchestration |
| **Gradle Wrapper** | Latest | Build automation |
| **Version Catalog** | `gradle/libs.versions.toml` | Centralized dependency management |

### Testing

| Framework | Version | Purpose |
|-----------|---------|---------|
| **JUnit** | 4.13.2 | Unit testing |
| **AndroidX Test** | 1.3.0 | Instrumentation testing |
| **Espresso** | 3.7.0 | UI testing |
| **Compose UI Test** | Latest (via BOM) | Composable UI testing |

### Flutter Plugin (Separate Module)

- **Environment**: Dart 3.11.4+, Flutter 3.3.0+
- **Purpose**: Wraps ML Kit speech recognition for Flutter apps
- **Status**: Skeleton implementation (method channel placeholder)

---

## Code Organization

### Directory Structure

```
/Users/nunokawa/Devel/SpeechRecognitionAPIStudy/
├── app/                                    # Main Android application module
│   ├── src/main/
│   │   ├── AndroidManifest.xml            # App manifest with RECORD_AUDIO permission
│   │   ├── java/org/nunocky/speechrecognitionapistudy/
│   │   │   ├── MainActivity.kt             # Entry point with Compose setup
│   │   │   ├── AppNavHost.kt               # Navigation routing (Menu, Mic, File screens)
│   │   │   ├── locale/
│   │   │   │   └── SupportedSpeechLocales.kt  # 15+ language locale definitions
│   │   │   ├── ui/
│   │   │   │   ├── main/
│   │   │   │   │   └── StartupMenuScreen.kt   # Main menu with mode selection
│   │   │   │   ├── mic/
│   │   │   │   │   ├── SpeechRecognitionScreen.kt   # Real-time mic transcription UI
│   │   │   │   │   └── SpeechRecognitionViewModel.kt # Mic mode state & logic
│   │   │   │   ├── file/
│   │   │   │   │   ├── FileToTtsScreen.kt         # File selection & transcription UI
│   │   │   │   │   ├── FileToTtsViewModel.kt      # File mode state & recognition logic
│   │   │   │   │   ├── FileAudioProcessor.kt      # Audio decoding & PCM conversion
│   │   │   │   │   ├── ResultBuffer.kt            # Text aggregation utility
│   │   │   │   ├── component/
│   │   │   │   │   ├── ChatBubble.kt              # Reusable chat message UI
│   │   │   │   │   ├── UIChatMessage.kt           # Chat message data model
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt                   # Compose theme setup
│   │   │   │   │   ├── Color.kt                   # Color palette
│   │   │   │   │   ├── Type.kt                    # Typography styles
│   │   ├── res/                            # Android resources
│   │   │   ├── values/strings.xml         # Default (Japanese) strings
│   │   │   ├── values-en/strings.xml      # English translations
│   │   │   ├── colors.xml, themes.xml     # Theme colors
│   │   │   ├── mipmap/ic_launcher*        # App launcher icons
│   │   ├── androidTest/java/...          # Instrumentation tests
│   │   └── test/java/...                 # Unit tests
│   ├── build.gradle.kts                   # App module build configuration
│   └── proguard-rules.pro                 # ProGuard/R8 rules
│
├── flutter_genai_mlkit_speech_recognition/  # Flutter plugin wrapper
│   ├── lib/
│   │   ├── flutter_genai_mlkit_speech_recognition.dart
│   │   ├── flutter_genai_mlkit_speech_recognition_platform_interface.dart
│   │   ├── flutter_genai_mlkit_speech_recognition_method_channel.dart
│   ├── android/
│   │   └── src/main/kotlin/...FlutterGenaiMlkitSpeechRecognitionPlugin.kt
│   ├── example/                           # Flutter example app
│   └── pubspec.yaml                       # Flutter package manifest
│
├── gradle/
│   └── libs.versions.toml                 # Centralized dependency versions
│
├── .kiro/                                 # Spec-driven development system
│   ├── steering/                          # Project-wide guidelines
│   │   ├── product.md                     # Product vision & capabilities
│   │   ├── tech.md                        # Technology decisions & standards
│   │   └── structure.md                   # Code organization patterns
│   └── specs/                             # Feature specifications
│       ├── file-to-tts/                   # File transcription feature spec
│       ├── speech-recognition-api-integration/
│       ├── i18n-strings-xml/
│       └── startup-menu-tts-modes/
│
├── build.gradle.kts                       # Root Gradle config
├── settings.gradle.kts                    # Module configuration
├── gradle.properties                      # Global Gradle settings
├── README.md                              # Project documentation
├── AGENTS.md                              # AI-DLC spec-driven workflow guide
└── CLAUDE.md                              # This file
```

### Key Package Organization

#### `ui/mic/` - Microphone Transcription
- **SpeechRecognitionScreen.kt**: Composable UI
  - Displays locale selector, record button, partial/final transcriptions
  - Requests and checks RECORD_AUDIO permission
  - Collects ViewModel state and events

- **SpeechRecognitionViewModel.kt**: State management
  - `SpeechRecognitionUiState`: isListening, partialText, messages
  - `startListening()`: Creates mic AudioSource, starts recognition stream
  - `stopListening()`: Cancels job, stops recognizer
  - Handles locale changes with recognizer recreation
  - Emits errors and version incompatibility events

#### `ui/file/` - File Transcription
- **FileToTtsScreen.kt**: Composable UI
  - File picker, playback controls, transcription display
  - Combines file selection, audio playback, and recognition

- **FileToTtsViewModel.kt**: State management
  - `FileToTtsUiState`: selectedFileName, isProcessing, isRecognizing, isPlaying, messages
  - `onFileSelected(uri)`: Clears previous results, stores selected file
  - `togglePlayback()`: MediaPlayer-based audio playback
  - `startRecognition()`: Main flow for file recognition
    - Ensures recognizer is ready (checks/downloads model)
    - Creates temp PCM file via FileAudioProcessor
    - Opens pipe, streams PCM data asynchronously
    - Collects recognition responses and aggregates results
    - Properly cleans up resources in finally block
  - ResultBuffer: Aggregates final text chunks into complete messages

- **FileAudioProcessor.kt**: Audio processing utilities
  - `createTempPcmFile(app, uri)`: 
    - Uses MediaExtractor to demultiplex audio track
    - Uses MediaCodec to decode any format (MP3, AAC, FLAC, OGG, WAV)
    - `convertToTargetPcm()`: Converts to 16kHz mono 16-bit PCM
      - Handles 8-bit, 16-bit, and float PCM input
      - Multi-channel to mono downmixing
      - Sample rate resampling via linear interpolation
  - `streamPcmFileToPipe()`: Streams PCM file to ParcelFileDescriptor
    - Chunked streaming at 4x realtime speed
    - Delayed writes to avoid overwhelming recognizer

- **ResultBuffer.kt**: Utility for text aggregation
  - Collects final text responses into complete message
  - Clears between recognition runs

#### `ui/component/` - Reusable UI Components
- **ChatBubble.kt**: Styled message display
- **UIChatMessage.kt**: Data model (text, timestamp)

#### `ui/theme/` - Design System
- **Theme.kt**: Material 3 theme setup with light/dark modes
- **Color.kt**: Color palette definitions
- **Type.kt**: Typography (headline, body, label styles)

#### `locale/` - Internationalization
- **SupportedSpeechLocales.kt**:
  - DefaultLocaleTag = "ja-JP"
  - 15 supported locales with display names
  - sanitize() validates and defaults invalid tags

### Naming Conventions

| Category | Convention | Examples |
|----------|-----------|----------|
| **File Names** | PascalCase matching primary type | `MainActivity.kt`, `SpeechRecognitionScreen.kt` |
| **Composables** | PascalCase function names | `SpeechRecognitionScreen()`, `ChatBubble()` |
| **Data Models** | PascalCase with descriptive prefix | `UIChatMessage`, `SpeechRecognitionUiState` |
| **State Classes** | UiState suffix for ViewModel state | `FileToTtsUiState`, `SpeechRecognitionUiState` |
| **Event Classes** | UiEvent sealed class, PascalCase variants | `FileToTtsUiEvent.ShowError()` |
| **Functions/Variables** | camelCase | `startListening()`, `partialText` |
| **Constants** | UPPER_SNAKE_CASE | `TARGET_SAMPLE_RATE`, `STREAM_CHUNK_BYTES` |

### Import Organization

Imports follow this order:
1. Kotlin standard library
2. Android framework APIs
3. AndroidX libraries
4. Third-party libraries (Compose, ML Kit)
5. Project-local imports

Prefer explicit imports (no wildcard imports).

---

## Key Components

### 1. SpeechRecognitionScreen & ViewModel (Microphone Mode)

**Purpose**: Real-time transcription from device microphone

**Flow**:
1. User selects locale from dropdown
2. User taps record button → `startListening()`
3. Creates `AudioSource.fromMic()` with default mic device
4. Creates `SpeechRecognizerRequest` with audio source
5. Streams recognition responses:
   - `PartialTextResponse`: Updates UI partial text in real-time
   - `FinalTextResponse`: Adds complete phrase to message list
   - `ErrorResponse`: Emits error event for toast display
   - `CompletedResponse`: Stops listening, cleans up
6. User taps stop → `stopListening()` cancels job

**Key Code Locations**:
- ViewModel: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/ui/mic/SpeechRecognitionViewModel.kt` (lines 36-137)
- Screen: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/ui/mic/SpeechRecognitionScreen.kt`

**Platform Requirements**:
- Android 12+ (API 31+) - gated with `Build.VERSION.SDK_INT` check
- `android.permission.RECORD_AUDIO` declared in manifest
- Runtime permission request before `startListening()`

### 2. FileToTtsScreen & ViewModel (File Mode)

**Purpose**: Transcribe pre-recorded audio files

**Flow**:
1. User taps "Select File" → Opens file picker (not shown, external intent)
2. `onFileSelected(uri)` stores file and clears previous results
3. Optional: User taps play button → `togglePlayback()` plays audio via MediaPlayer
4. User taps transcribe → `startRecognition()`:
   - Ensures recognizer is ready (downloads model if needed)
   - Calls `createTempPcmFile()` to decode file
   - Creates pipe with `ParcelFileDescriptor.createPipe()`
   - Launches async streaming job with `streamPcmFileToPipe()`
   - Recognizer reads from pipe while streaming happens
   - Collects responses and aggregates final text
   - Cleans up resources in finally block
5. Results displayed in chat UI

**Key Features**:
- **File Format Support**: MP3, AAC, M4A, OGG (Opus/Vorbis), FLAC, WAV
- **Audio Decoding**: MediaExtractor → MediaCodec pipeline
- **PCM Conversion**: Any format/sample rate/channels → 16kHz mono 16-bit PCM
- **Non-blocking Streaming**: Async pipe streaming with paced delivery (4x realtime)
- **Result Aggregation**: ResultBuffer concatenates final text responses

**Key Code Locations**:
- ViewModel: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/ui/file/FileToTtsViewModel.kt` (lines 111-410)
- FileAudioProcessor: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/ui/file/FileAudioProcessor.kt` (lines 1-270)
- Screen: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/ui/file/FileToTtsScreen.kt`

### 3. FileAudioProcessor - Audio Decoding & Conversion

**Core Algorithm** (`createTempPcmFile`):

```kotlin
1. MediaExtractor.setDataSource(uri) → find audio track
2. Extract format (sample rate, channels, codec)
3. MediaCodec.createDecoderByType(mime) → configure & start
4. Loop:
   - dequeueInputBuffer() → feed compressed data
   - dequeueOutputBuffer() → read decoded PCM
   - convertToTargetPcm() → normalize format
   - Write to temp file
5. Close temp file → return path
```

**Conversion Logic** (`convertToTargetPcm`):

- **Input**: Arbitrary PCM format (8/16/float bit, N channels, M Hz)
- **Output**: 16-bit mono 16 kHz PCM
- **Steps**:
  1. Parse input encoding (8-bit unsigned, 16-bit LE, float)
  2. For each input frame:
     - Average all channels into mono
     - Scale samples to -32768..32767 range
  3. If sample rate differs:
     - Linear interpolation between frames
     - Resample to 16000 Hz
  4. Encode output as little-endian 16-bit

**Supported Audio Formats**:

| Format | Container | Codec | Notes |
|--------|-----------|-------|-------|
| **WAV (Recommended)** | RIFF | PCM 16-bit 16kHz mono | Best recognition accuracy |
| **MP3** | MP3 | MPEG Layer 3 | Decoded via `audio/mpeg` |
| **AAC** | M4A | AAC-LC | Decoded via `audio/mp4a-latm` |
| **OGG Opus** | OGG | Opus | Decoded via `audio/opus` |
| **OGG Vorbis** | OGG | Vorbis | Decoded via `audio/vorbis` |
| **FLAC** | FLAC | FLAC | Decoded via `audio/flac` |

**Key Code**: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/ui/file/FileAudioProcessor.kt` (lines 44-237)

### 4. Navigation & Routing (AppNavHost)

**Routes**:

| Route | Purpose | Args | Default |
|-------|---------|------|---------|
| `menu` | Main menu screen | None | Startup destination |
| `mic_to_tts?localeTag={tag}` | Microphone mode | localeTag (String) | ja-JP |
| `file_to_tts?localeTag={tag}` | File mode | localeTag (String) | ja-JP |

**Navigation Flow**:
```
Menu → Select Locale → Mic/File Screen
      → Back Stack → Menu
```

**Key Code**: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/AppNavHost.kt` (lines 15-74)

### 5. Locale Support

**Supported Languages** (15 total):

| Locale | Display Name | Status |
|--------|--------------|--------|
| en-US | English | Stable |
| ja-JP | 日本語 | Beta (Default) |
| fr-FR, it-IT, de-DE, es-ES | European languages | Beta |
| hi-IN | हिन्दी | Beta |
| pt-BR | Português | Beta |
| tr-TR | Türkçe | Beta |
| pl-PL | Polski | Beta |
| cmn-Hans-CN, cmn-Hant-TW | Chinese (Simplified/Traditional) | Beta |
| ko-KR | 한국어 | Beta |
| ru-RU | Русский | Beta |
| vi-VN | Tiếng Việt | Beta |

**Implementation**: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/locale/SupportedSpeechLocales.kt`

**Locale Passing**: Passed through navigation args, validated with `sanitize()` function to prevent invalid locale crashes.

---

## Development Workflow

### Prerequisites

- **Android Studio** (Latest with Android SDK 36+)
- **JDK 11** or compatible
- **Gradle Wrapper** (included in repo)
- **Android Emulator** (API 31+) or physical device (Android 12+)

### Initial Setup

```bash
# Clone repository
git clone <repo-url>
cd SpeechRecognitionAPIStudy

# Sync Gradle and download dependencies
./gradlew sync

# Optional: Setup Flutter plugin (if developing flutter integration)
cd flutter_genai_mlkit_speech_recognition
flutter pub get
```

### Project Management System: Kiro Spec-Driven Development

This project uses **Kiro**, a spec-driven development (SDD) system:

**Key Directories**:
- `.kiro/steering/`: Project-wide guidelines (product, tech, structure)
- `.kiro/specs/`: Feature specifications with requirements, design, tasks
- `.kiro/settings/`: Rules and templates for spec generation

**Workflow**:
1. Initialize spec: `/kiro-spec-init "description"`
2. Define requirements: `/kiro-spec-requirements {feature}`
3. Validate gaps: `/kiro-validate-gap {feature}` (against existing code)
4. Design: `/kiro-spec-design {feature}`
5. Generate tasks: `/kiro-spec-tasks {feature}`
6. Implement: `/kiro-spec-impl {feature} [tasks]`

**Example Specifications**:
- `file-to-tts/` - File transcription feature
- `speech-recognition-api-integration/` - API integration guidelines
- `i18n-strings-xml/` - Internationalization
- `startup-menu-tts-modes/` - UI navigation

See `AGENTS.md` for detailed workflow documentation.

---

## Common Commands

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (unsigned)
./gradlew assembleRelease

# Build and install on connected device
./gradlew installDebug

# Rebuild everything (clean build)
./gradlew clean build
```

### Testing Commands

```bash
# Run unit tests (local JVM)
./gradlew testDebugUnitTest

# Run instrumentation tests (on device/emulator)
./gradlew connectedDebugAndroidTest

# Run all tests
./gradlew test connectedAndroidTest

# Run specific test class
./gradlew testDebugUnitTest --tests FileToTtsViewModelFileSelectionTest
```

### Development Commands

```bash
# Check for lint issues
./gradlew lintDebug

# Format Kotlin code (with Ktlint if configured)
./gradlew formatKotlin

# Analyze dependencies
./gradlew dependencies

# Generate dependency report
./gradlew dependencyReport
```

### Gradle Tasks (Utility)

```bash
# List all available tasks
./gradlew tasks

# Show task dependency graph for assembleDebug
./gradlew assembleDebug --dry-run

# Execute with verbose output
./gradlew assembleDebug --info
```

### Device/Emulator Commands

```bash
# List connected devices
adb devices

# Push a test audio file to device
adb push test_audio.wav /sdcard/Downloads/

# View app logs
adb logcat -s "FileToTtsViewModel"

# Clear app data
adb shell pm clear org.nunocky.speechrecognitionapistudy
```

---

## Project Configuration

### Gradle Build Configuration

**Root File**: `build.gradle.kts`
- Plugin aliases for Android Application and Kotlin Compose
- Version management via Catalog

**App Module**: `app/build.gradle.kts`
```kotlin
- Namespace: org.nunocky.speechrecognitionapistudy
- Target API: 36
- Min API: 28
- Java Target: 11
- Compose Feature: Enabled
- Dependencies: Core KTX, Compose, Lifecycle, Navigation, ML Kit
```

**Version Catalog**: `gradle/libs.versions.toml`
- Centralized dependency versions
- Prevents version mismatch across modules
- Easy to update multiple libraries at once

**Gradle Properties**: `gradle.properties`
```
JVM Heap: 2048m
Kotlin Style: official
File Encoding: UTF-8
```

### Android Manifest

**Location**: `app/src/main/AndroidManifest.xml`

**Key Permissions**:
- `android.permission.RECORD_AUDIO` - Required for microphone access

**Activities**:
- `MainActivity` - Exported, default launcher activity

**Theme**: 
- Material 3 theme applied globally

### Module Dependencies

**Direct Dependencies** (via libs.versions.toml):

Core Android:
- androidx.core:core-ktx
- androidx.lifecycle:lifecycle-runtime-ktx
- androidx.lifecycle:lifecycle-viewmodel-compose
- androidx.activity:activity-compose

Compose:
- androidx.compose.*:ui
- androidx.compose.*:material3
- androidx.compose.material:material-icons-extended
- androidx.navigation:navigation-compose

Speech Recognition:
- com.google.mlkit:genai-speech-recognition (1.0.0-alpha1)

Testing:
- junit (unit tests)
- androidx.test.ext:junit (instrumentation)
- androidx.test.espresso:espresso-core (UI testing)
- androidx.compose.ui:ui-test-* (Compose testing)

### Resource Configuration

**Strings** (i18n):
- `res/values/strings.xml` - Default (Japanese) strings
- `res/values-en/strings.xml` - English translations
- Localizable: App name, button labels, error messages

**Theme Colors**: `res/values/colors.xml`
- Primary, secondary, tertiary colors
- Surface colors for Material 3

**Themes**: `res/values/themes.xml`
- Light and dark theme definitions

**Icons**: `res/mipmap/ic_launcher*`
- App launcher icons and adaptive icons

---

## Special Project Management

### Kiro Spec-Driven Development System

**What is Kiro?**

A structured specification and task generation system for AI-assisted development:
- Formalizes requirements → design → implementation phases
- Each feature has a dedicated spec directory
- AI can validate implementation against spec

**Steering vs Specs**:

**Steering** (`.kiro/steering/`):
- Project-wide guidelines and context
- Lives throughout project lifetime
- 3 main files:
  - `product.md` - Product vision, use cases, value prop
  - `tech.md` - Technology stack, standards, development practices
  - `structure.md` - Code organization, naming conventions, patterns

**Specs** (`.kiro/specs/{feature}/`):
- Individual feature specifications
- Lifecycle: requirements → design → tasks → implementation
- Each feature has:
  - `spec.json` - Metadata (name, language, phase)
  - `requirements.md` - What the feature should do
  - `design.md` - How the feature should be built
  - `tasks.md` - Actionable work items
  - `research.md` - Investigation notes (optional)

**Active Specifications**:

1. **file-to-tts/** - File audio transcription feature
   - Requirements: File selection, audio playback, recognition
   - Design: FileToTtsViewModel, FileAudioProcessor, UI components
   - Tasks: Feature implementation steps
   - Status: Fully implemented

2. **speech-recognition-api-integration/** - API integration
   - Requirements: ML Kit integration, response handling
   - Status: Core specifications only

3. **i18n-strings-xml/** - Internationalization
   - Requirements: Multi-language support
   - Status: Implemented with 15 locales

4. **startup-menu-tts-modes/** - Navigation & mode selection
   - Requirements: Menu UI, locale selection, route passing
   - Status: Implemented

### Workflow in This Project

The project follows a **3-phase approval workflow**:

1. **Phase 1 - Specification**:
   - Requirements reviewed and formalized
   - Design validated against requirements
   - Task list generated and reviewed

2. **Phase 2 - Implementation**:
   - Tasks executed following spec
   - Implementation validated against design
   - Code reviewed for spec compliance

3. **Progress Tracking**:
   - Use `/kiro-spec-status {feature}` to check phase
   - Steering kept current with `/kiro-steering`

**Key Files for AI Context**:

When working with AI agents on this codebase:
- Load `.kiro/steering/product.md` for product context
- Load `.kiro/steering/tech.md` for technical standards
- Load `.kiro/steering/structure.md` for code organization
- Reference specific spec files (e.g., `.kiro/specs/file-to-tts/`) for feature work

---

## Code Patterns & Best Practices

### Pattern: MVVM with Compose

**ViewModel Definition**:
```kotlin
data class UiState(val isListening: Boolean = false, ...)

sealed class UiEvent {
    data class ShowError(val message: String) : UiEvent()
}

class MyViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()
    
    fun updateState() {
        _uiState.update { it.copy(isListening = true) }
    }
}
```

**Screen Collection**:
```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowError -> showErrorDialog(event.message)
            }
        }
    }
    
    // Render based on uiState
}
```

### Pattern: Structured Concurrency with Jobs

**Correct Usage**:
```kotlin
private var recognitionJob: Job? = null

fun startRecognition() {
    if (recognitionJob != null) return  // Already running
    
    recognitionJob = viewModelScope.launch {
        try {
            // Do work
        } catch (_: CancellationException) {
            // Expected control flow
        } finally {
            recognitionJob = null  // Always cleanup
        }
    }
}

fun stopRecognition() {
    recognitionJob?.cancel()
    recognitionJob = null
}
```

### Pattern: Resource Cleanup with Finally

**File Operations**:
```kotlin
var resource: SomeResource? = null
try {
    resource = acquire()
    use(resource)
} finally {
    runCatching { resource?.close() }  // Ignore errors on cleanup
}
```

### Pattern: Immutable State Updates

**Use `copy()`**:
```kotlin
// Good
_uiState.update { it.copy(isListening = true, partialText = "") }

// Avoid
_uiState.value = _uiState.value.copy(...)  // Race condition
```

### Pattern: Locale Validation

**Always sanitize user-provided locale tags**:
```kotlin
val localeTag = SupportedSpeechLocales.sanitize(userInput)  // Returns default if invalid
val locale = Locale.forLanguageTag(localeTag)
```

### Pattern: API Level Gating

```kotlin
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {  // API 31+
    emitEvent(UiEvent.UnsupportedVersion)
    return
}
```

---

## Testing

### Unit Tests

**Location**: `app/src/test/java/`

**Examples**:
- `FileToTtsViewModelFileSelectionTest.kt` - File selection state updates
- `FileToTtsScreenUnitTest.kt` - Screen composition logic
- `ExampleUnitTest.kt` - Basic test template

**Run**: `./gradlew testDebugUnitTest`

### Instrumentation Tests

**Location**: `app/src/androidTest/java/`

**Examples**:
- `FileToTtsScreenStateTest.kt` - UI state verification on device
- `FileToTtsScreenScaffoldTest.kt` - Scaffold layout testing
- `ExampleInstrumentedTest.kt` - Device API testing

**Run**: `./gradlew connectedDebugAndroidTest`

### Test Coverage Areas

Based on codebase, tests should focus on:
1. **ViewModel state transitions** - Verify state updates correctly
2. **Permission handling** - Ensure RECORD_AUDIO checks
3. **API level gating** - Verify Android 12+ compatibility
4. **File selection flow** - Clear previous results when selecting new file
5. **Recognition cleanup** - Resources freed properly
6. **Locale validation** - Invalid locales default correctly
7. **Audio decoding** - Various formats convert to PCM correctly

---

## Troubleshooting

### Common Issues

**"Speech Recognition feature not available"**
- Model may not be downloaded yet
- Call `recognizer.download()` to download on-device model
- Requires first-time network access

**"RECORD_AUDIO permission denied"**
- Request permission at runtime before `startListening()`
- Declare in AndroidManifest.xml

**"Unsupported API version"**
- App requires Android 12 (API 31) minimum for speech recognition
- Check `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`

**"File decoding failed"**
- Ensure file is valid audio format
- Check MediaExtractor can find audio track
- Verify codec is available on device

**"Transcription accuracy is low"**
- Ideal format: 16 kHz mono 16-bit PCM
- Check actual file sample rate/channels
- Noisy audio affects recognition
- Language/locale mismatch affects accuracy

### Debug Logging

**Enable logs**:
```bash
./gradlew assembleDebug

adb logcat -s "FileToTtsViewModel" -s "FileAudioProcessor"
```

**Key log tags**:
- `FileToTtsViewModel` - Recognition state, errors
- `FileAudioProcessor` - File decoding, PCM conversion
- `MediaPlayer` - Audio playback errors

---

## References & Resources

### Official Documentation

- [ML Kit GenAI Speech Recognition](https://developers.google.com/ml-kit/genai/speech-recognition/android?hl=ja)
- [Jetpack Compose Documentation](https://developer.android.com/compose)
- [Android Audio Processing](https://developer.android.com/guide/topics/media/mediacodec)
- [Android Permissions](https://developer.android.com/guide/topics/permissions)

### Project-Specific Documentation

- `README.md` - Project overview, audio format recommendations
- `AGENTS.md` - Kiro spec-driven development workflow
- `.kiro/steering/` - Project guidelines (product, tech, structure)
- `.kiro/specs/*/` - Feature specifications and task definitions

### Related Technologies

- **Kotlin Coroutines**: https://kotlinlang.org/docs/coroutines-overview.html
- **Flow & StateFlow**: https://kotlinlang.org/docs/flow.html
- **Jetpack Lifecycle**: https://developer.android.com/guide/components/activities/activity-lifecycle
- **Navigation Compose**: https://developer.android.com/develop/ui/compose/navigation

---

## Summary

This is a **well-structured Android study application** demonstrating ML Kit speech recognition integration with modern Android development practices. It balances learning clarity with practical considerations (permissions, lifecycle, error handling, multiple audio formats).

**Key Strengths**:
- Clear feature separation (mic vs file modes)
- Comprehensive audio format support
- Proper resource cleanup and lifecycle management
- Multilingual support (15 languages)
- Good documentation and spec-driven development

**For Contributors**:
- Reference `.kiro/steering/` for project guidelines
- Follow MVVM + Compose patterns in `ui/mic/` and `ui/file/`
- Use the Kiro spec system for new features
- Test API level requirements and permissions
- Keep audio processing in dedicated utilities (FileAudioProcessor)

