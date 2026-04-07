package org.nunocky.speechrecognitionapistudy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nunocky.speechrecognitionapistudy.ui.component.UIChatMessage
import org.nunocky.speechrecognitionapistudy.ui.file.ResultBuffer
import org.nunocky.speechrecognitionapistudy.ui.file.FileToTtsUiState
import org.nunocky.speechrecognitionapistudy.ui.file.applyCompletedResponseAggregation
import org.nunocky.speechrecognitionapistudy.ui.file.applyErrorOrCancelReset
import org.nunocky.speechrecognitionapistudy.ui.file.applyFinalTextResponseAggregation
import org.nunocky.speechrecognitionapistudy.ui.file.canStartRecognition
import org.nunocky.speechrecognitionapistudy.ui.file.markRecognitionStarted

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

    // --- ResultBuffer model ---

    @Test
    fun resultBuffer_returnsEmptyString_whenNoResultsAdded() {
        val buffer = ResultBuffer()

        assertEquals("", buffer.getAggregated())
    }

    @Test
    fun resultBuffer_joinsTrimmedResults_withSingleSpaces() {
        val buffer = ResultBuffer()

        buffer.add("  first result  ")
        buffer.add("second result")
        buffer.add("   third result")

        assertEquals("first result second result third result", buffer.getAggregated())
    }

    @Test
    fun resultBuffer_clear_removesPreviouslyAddedResults() {
        val buffer = ResultBuffer()

        buffer.add("first result")
        buffer.clear()

        assertEquals("", buffer.getAggregated())
    }

    @Test
    fun finalTextResponse_isAccumulatedWithoutAppendingMessageImmediately() {
        val buffer = ResultBuffer()
        val before = FileToTtsUiState(
            partialText = "intermediate",
            messages = listOf(UIChatMessage("existing"))
        )

        val after = applyFinalTextResponseAggregation(
            currentState = before,
            finalText = "  final segment  ",
            resultBuffer = buffer
        )

        assertEquals("final segment", buffer.getAggregated())
        assertEquals(before.messages, after.messages)
        assertEquals("", after.partialText)
    }

    @Test
    fun completedResponse_appendsSingleAggregatedMessage_andResetsFlags() {
        val buffer = ResultBuffer().apply {
            add(" first")
            add("second ")
        }
        val before = FileToTtsUiState(
            isProcessing = true,
            isRecognizing = true,
            partialText = "intermediate",
            messages = listOf(UIChatMessage("existing"))
        )

        val after = applyCompletedResponseAggregation(
            currentState = before,
            resultBuffer = buffer
        )

        assertEquals(2, after.messages.size)
        assertEquals(UIChatMessage("first second"), after.messages.last())
        assertEquals("", after.partialText)
        assertFalse(after.isProcessing)
        assertFalse(after.isRecognizing)
    }

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

        // --- FileToTtsUiState model ---

        /**
         * isRecognizing のデフォルト値は false であることを確認する。
         * Requirement: 3
         */
        @Test
        fun fileToTtsUiState_isRecognizing_defaultIsFalse() {
            val state = FileToTtsUiState()
            assertFalse(state.isRecognizing)
        }

        /**
         * isRecognizing を true にコピーできることを確認する。
         * Requirement: 3
         */
        @Test
        fun fileToTtsUiState_isRecognizing_canBeSetToTrue() {
            val state = FileToTtsUiState().copy(isRecognizing = true)
            assertTrue(state.isRecognizing)
        }

        /**
         * isRecognizing = false にコピーできることを確認する。
         * Requirement: 3
         */
        @Test
        fun fileToTtsUiState_isRecognizing_canBeSetToFalse() {
            val state = FileToTtsUiState(isRecognizing = true).copy(isRecognizing = false)
            assertFalse(state.isRecognizing)
        }

        @Test
        fun canStartRecognition_returnsFalse_whenRecognitionJobExists() {
            val state = FileToTtsUiState(isRecognizing = false)

            val canStart = canStartRecognition(
                currentState = state,
                hasRecognitionJob = true
            )

            assertFalse(canStart)
        }

        @Test
        fun canStartRecognition_returnsFalse_whenRecognizingFlagIsTrue() {
            val state = FileToTtsUiState(isRecognizing = true)

            val canStart = canStartRecognition(
                currentState = state,
                hasRecognitionJob = false
            )

            assertFalse(canStart)
        }

        @Test
        fun canStartRecognition_returnsTrue_whenNotRecognizingAndNoJob() {
            val state = FileToTtsUiState(isRecognizing = false)

            val canStart = canStartRecognition(
                currentState = state,
                hasRecognitionJob = false
            )

            assertTrue(canStart)
        }

        @Test
        fun markRecognitionStarted_setsIsRecognizingTrue_andClearsPartialText() {
            val before = FileToTtsUiState(
                isProcessing = false,
                isRecognizing = false,
                partialText = "partial"
            )

            val after = markRecognitionStarted(before)

            assertTrue(after.isRecognizing)
            assertTrue(after.isProcessing)
            assertEquals("", after.partialText)
        }

        // --- Task 5.2: error/cancel reset ---

        /**
         * Task 5.2: CancellationException / ErrorResponse 時に ResultBuffer がクリアされる
         * Requirements: 1, 3, 4
         */
        @Test
        fun applyErrorOrCancelReset_clearsResultBuffer() {
            val buffer = ResultBuffer().apply {
                add("partial result 1")
                add("partial result 2")
            }
            val before = FileToTtsUiState(isRecognizing = true, isProcessing = true)

            applyErrorOrCancelReset(before, buffer)

            assertEquals("", buffer.getAggregated())
        }

        /**
         * Task 5.2: error/cancel 時に isRecognizing が false にリセットされる
         * Requirements: 3
         */
        @Test
        fun applyErrorOrCancelReset_setsIsRecognizingFalse() {
            val buffer = ResultBuffer()
            val before = FileToTtsUiState(isRecognizing = true, isProcessing = true)

            val after = applyErrorOrCancelReset(before, buffer)

            assertFalse(after.isRecognizing)
        }

        /**
         * Task 5.2: error/cancel 時に isProcessing が false にリセットされる
         * Requirements: 3, 4
         */
        @Test
        fun applyErrorOrCancelReset_setsIsProcessingFalse() {
            val buffer = ResultBuffer()
            val before = FileToTtsUiState(isRecognizing = true, isProcessing = true)

            val after = applyErrorOrCancelReset(before, buffer)

            assertFalse(after.isProcessing)
        }

        /**
         * Task 5.2: error/cancel 時に partialText がクリアされる
         * Requirements: 3, 4
         */
        @Test
        fun applyErrorOrCancelReset_clearsPartialText() {
            val buffer = ResultBuffer()
            val before = FileToTtsUiState(
                isRecognizing = true,
                isProcessing = true,
                partialText = "in-progress text"
            )

            val after = applyErrorOrCancelReset(before, buffer)

            assertEquals("", after.partialText)
        }

        /**
         * Task 5.2: error/cancel 時に既存の messages リストは保持される
         * Requirements: 4
         */
        @Test
        fun applyErrorOrCancelReset_preservesExistingMessages() {
            val buffer = ResultBuffer().apply { add("partial") }
            val existingMessages = listOf(UIChatMessage("previous message"))
            val before = FileToTtsUiState(
                selectedFileName = "test.mp3",
                isRecognizing = true,
                isProcessing = true,
                messages = existingMessages
            )

            val after = applyErrorOrCancelReset(before, buffer)

            assertEquals(existingMessages, after.messages)
            assertEquals("test.mp3", after.selectedFileName)
        }

        /**
         * Task 5.2: バッファが空の状態でもリセットが安全に動作する
         * Requirements: 3
         */
        @Test
        fun applyErrorOrCancelReset_safeWhenBufferAlreadyEmpty() {
            val buffer = ResultBuffer()
            val before = FileToTtsUiState(isRecognizing = true, isProcessing = true)

            val after = applyErrorOrCancelReset(before, buffer)

            assertFalse(after.isRecognizing)
            assertFalse(after.isProcessing)
            assertEquals("", buffer.getAggregated())
        }

        // --- Task 6.1: 重複実行防止ダブルチェックの検証 ---

        /**
         * Task 6.1: recognitionJob と isRecognizing の両方が active な場合、
         * canStartRecognition は false を返す（通常の認識進行中状態）。
         * 既存の recognitionJob チェックに加え isRecognizing フラグでも防止されることを確認。
         * Requirements: 5
         */
        @Test
        fun canStartRecognition_returnsFalse_whenBothJobExistsAndIsRecognizingIsTrue() {
            val state = FileToTtsUiState(isRecognizing = true)

            val canStart = canStartRecognition(
                currentState = state,
                hasRecognitionJob = true
            )

            assertFalse(canStart)
        }

        /**
         * Task 6.1: 認識進行中にユーザーが新しいファイルを選択した場合、
         * onFileSelected 後の状態でも startRecognition はガードされる。
         *
         * Scenario:
         * 1. 認識中: isRecognizing = true, recognitionJob != null
         * 2. ユーザーが新規ファイルを選択 (onFileSelected 相当の状態変化)
         *    - selectedFileName が更新される
         *    - partialText がクリアされる
         *    - messages がクリアされる
         *    - isRecognizing は変化しない (認識は継続中)
         * 3. この状態で startRecognition() を呼ぼうとしてもガードされる
         * Requirements: 5
         */
        @Test
        fun canStartRecognition_returnsFalse_afterFileSelectionDuringActiveRecognition() {
            // onFileSelected 後の状態をシミュレート: 認識は継続中
            val stateAfterFileSelection = FileToTtsUiState(
                selectedFileName = "new_audio.mp3",
                isRecognizing = true,   // 認識は継続中 (onFileSelected では変更されない)
                partialText = "",       // onFileSelected でクリア済み
                messages = emptyList()  // onFileSelected でクリア済み
            )

            val canStart = canStartRecognition(
                currentState = stateAfterFileSelection,
                hasRecognitionJob = true  // 既存の認識ジョブが実行中
            )

            assertFalse(canStart)
        }

        /**
         * Task 6.1: isRecognizing のみ true の場合も（recognitionJob が null でも）
         * canStartRecognition は false を返す。
         * これにより、Job の参照が失われた edge case でも防止できることを確認。
         * Requirements: 5
         */
        @Test
        fun canStartRecognition_returnsFalse_whenOnlyIsRecognizingIsTrue() {
            val state = FileToTtsUiState(isRecognizing = true)

            val canStart = canStartRecognition(
                currentState = state,
                hasRecognitionJob = false  // Job 参照が失われた edge case
            )

            assertFalse(canStart)
        }
}
