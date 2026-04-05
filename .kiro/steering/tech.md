# Technology Stack

## Architecture

Single-module Android app with a Compose-first UI layer and direct integration to ML Kit speech recognition APIs. State is managed in Compose (`remember`/`mutableStateOf`) and recognition is driven by Kotlin coroutines/Flow collection.

## Core Technologies

- **Language**: Kotlin (JVM target 11)
- **Framework**: Android SDK + Jetpack Compose + Material 3
- **Build System**: Gradle Kotlin DSL with Version Catalog (`libs.versions.toml`)
- **Runtime Targets**: minSdk 28, target/compile SDK 36

## Key Libraries

- **ML Kit GenAI Speech Recognition** (`com.google.mlkit:genai-speech-recognition`): streaming speech-to-text events.
- **AndroidX Activity Compose**: activity + Compose hosting.
- **AndroidX Lifecycle Runtime KTX**: Android lifecycle coroutine utilities.
- **Compose BOM + Material 3**: consistent UI stack and versions.

## Development Standards

### API and Platform Handling
- Guard speech recognition execution by Android version checks where platform/API constraints apply.
- Request and validate `RECORD_AUDIO` permission before session start.
- Close/cleanup recognition client resources via lifecycle-aware effects.

### UI and State
- Prefer immutable model objects for rendered transcript items.
- Keep transient UI state (listening status, partial text) local and explicit.
- Reflect recognition event types (partial, final, completed, error) directly in UI behavior.

### Concurrency and Error Handling
- Use structured concurrency with a tracked `Job` for active recognition sessions.
- Treat cancellation as expected control flow; only surface actionable errors to users.
- Ensure start/stop transitions are idempotent to avoid duplicate collectors.

### Testing
- Unit and instrumentation test dependencies are configured; test coverage should prioritize recognition state transitions and permission/SDK edge cases.

## Development Environment

### Required Tools
- Android Studio with Android SDK 36 installed.
- JDK 11-compatible toolchain for project build settings.
- Gradle Wrapper (`./gradlew`) for all build and test operations.

### Common Commands
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
```

## Key Technical Decisions

- **Compose-first UI**: keeps the sample concise and modern for Android UI development.
- **Direct API integration in screen layer (current stage)**: optimized for learning/prototyping speed; extraction to dedicated domain/data layers can follow as complexity grows.
- **Version Catalog adoption**: centralizes dependency and plugin versions for predictable upgrades.

---
_Document standards and technical patterns, not every dependency._

