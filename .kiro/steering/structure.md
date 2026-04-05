# Project Structure

## Organization Philosophy

Lightweight, app-module-centric Android structure. Keep the codebase easy to navigate for iterative prototyping: UI and feature logic live close together, while reusable visual design definitions stay in the theme package.

## Directory Patterns

### Android App Module
**Location**: `/app/`  
**Purpose**: Contains all Android build config, manifest/resources, and source code for the application.  
**Example**: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`

### Feature/UI Package
**Location**: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/`  
**Purpose**: Main screen logic, composables, and UI-facing models for speech recognition behavior.  
**Example**: `SpeechRecognitionScreen.kt`, `ChatBubble.kt`, `UIChatMessage.kt`

### Theme Package
**Location**: `/app/src/main/java/org/nunocky/speechrecognitionapistudy/ui/theme/`  
**Purpose**: Shared visual tokens and theme setup for Compose Material design.  
**Example**: `Theme.kt`, `Color.kt`, `Type.kt`

### Build and Version Policy
**Location**: `/gradle/libs.versions.toml`, root Gradle files  
**Purpose**: Centralize dependency/plugin versions and repository/plugin management.  
**Example**: `gradle/libs.versions.toml`, `settings.gradle.kts`

## Naming Conventions

- **Files**: PascalCase for Kotlin source files aligned with primary type/composable (`MainActivity.kt`, `SpeechRecognitionScreen.kt`).
- **Composables**: PascalCase function names for UI components (`SpeechRecognitionScreen`, `ChatBubble`).
- **Models**: UI model types are concise and explicit (`UIChatMessage`).
- **Functions/Variables**: camelCase for local functions/state (`startListening`, `partialText`).

## Import Organization

```kotlin
import androidx.compose.runtime.Composable
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import org.nunocky.speechrecognitionapistudy.ui.theme.SpeechRecognitionAPIStudyTheme
```

- Prefer explicit imports (no wildcard imports).
- Keep package-local imports clear and stable; introduce aliases only if name collisions occur.

## Code Organization Principles

- Build screens around event-driven UI state updates from speech recognition responses.
- Keep lifecycle-sensitive setup/cleanup colocated with the composable that owns the client.
- Start with simple colocated logic; extract layers only when repeated logic or growth justifies abstraction.
- Preserve a clear split between theme primitives and feature behavior code.

---
_Document patterns, not file trees. New files that follow these conventions should not require steering updates._

