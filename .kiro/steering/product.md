# Product Overview

This project is an Android study app for evaluating on-device speech recognition with the ML Kit GenAI Speech Recognition API. It focuses on a minimal, understandable implementation that can be extended into production-like voice input flows.

## Core Capabilities

- Capture microphone audio from a Compose screen and stream recognition results.
- Render both partial and final transcripts in a chat-style timeline.
- Handle runtime microphone permission flow for Android.
- Start and stop recognition sessions from a single floating action button.
- Surface recognition/runtime errors to the user quickly.

## Target Use Cases

- Prototyping voice input UX in Android apps.
- Learning how to integrate ML Kit speech recognition with Jetpack Compose.
- Verifying API behavior across partial/final/completed response events.
- Creating a baseline implementation for later features (post-processing, command routing, conversation UX).

## Value Proposition

- Keeps the implementation intentionally small, so integration points are easy to understand.
- Demonstrates practical Android concerns (permission handling, lifecycle-safe cleanup, SDK gating) alongside speech recognition.
- Establishes a reusable foundation for future voice features without premature architecture complexity.

---
_Focus on product purpose and recurring value patterns, not exhaustive feature inventory._

