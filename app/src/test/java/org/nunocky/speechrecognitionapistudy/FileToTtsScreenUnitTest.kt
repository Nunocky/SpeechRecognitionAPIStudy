package org.nunocky.speechrecognitionapistudy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for file-to-tts feature — pure JVM (no Android/ML Kit dependency needed).
 *
 * Note: State transition tests (isProcessing, idempotency guard, CancellationException handling,
 * SDK version gate) require a mocked SpeechRecognizer and are covered as instrumented Compose UI
 * tests in FileToTtsScreenStateTest. A mocking framework (e.g. mockk) would be needed for
 * complete JVM-level coverage.
 *
 * Requirements: 2.3, 2.5, 3.3, 4.4, 5.2, 5.3
 */
class FileToTtsScreenUnitTest {

    // --- UIChatMessage model ---

    /**
     * UIChatMessage stores the provided text correctly.
     * Used as the transcript entry model throughout the feature.
     */
    @Test
    fun uiChatMessage_storesTextCorrectly() {
        val message = UIChatMessage("Hello, world!")
        assertEquals("Hello, world!", message.text)
    }

    /**
     * UIChatMessage equality is value-based (data class).
     */
    @Test
    fun uiChatMessage_equalityIsValueBased() {
        val a = UIChatMessage("test")
        val b = UIChatMessage("test")
        val c = UIChatMessage("other")

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    /**
     * UIChatMessage copy works correctly (data class contract).
     */
    @Test
    fun uiChatMessage_copyPreservesText() {
        val original = UIChatMessage("original")
        val copy = original.copy(text = "modified")

        assertEquals("original", original.text)
        assertEquals("modified", copy.text)
    }
}

