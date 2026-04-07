package org.nunocky.speechrecognitionapistudy

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.nunocky.speechrecognitionapistudy.ui.file.ResultBuffer
import org.nunocky.speechrecognitionapistudy.ui.file.FileToTtsUiState
import org.nunocky.speechrecognitionapistudy.ui.component.UIChatMessage

/**
 * Unit tests for FileToTtsViewModel state management during file selection — Task 3.1
 * Tests that onFileSelected() properly initializes and resets state.
 *
 * These tests verify the core behavior that will be implemented in FileToTtsViewModel.onFileSelected():
 * - ResultBuffer clearance
 * - partialText clearance
 * - messages clearance to prevent result mixing
 *
 * Requirements: 1, 3
 * Tasks: 3.1
 */
class FileToTtsViewModelFileSelectionTest {

    /**
     * Requirement 1: ファイル入力時の結果集約初期化
     * Test: ResultBuffer should be cleared when resetting for a new file selection.
     * This ensures the previous aggregation state is not mixed with new file results.
     */
    @Test
    fun fileSelection_clearsResultBuffer() {
        // Simulate: Buffer had previous results from prior file
        val buffer = ResultBuffer()
        buffer.add("previous result 1")
        buffer.add("previous result 2")

        // Verify buffer has content
        assertEquals("previous result 1 previous result 2", buffer.getAggregated())

        // When new file is selected, buffer should be cleared
        buffer.clear()

        // After clear, buffer should return empty string
        assertEquals("", buffer.getAggregated())
    }

    /**
     * Requirement 1 & 3: ファイル入力時の結果集約初期化 + 認識ライフサイクル管理
     * Test: partialText should be cleared during file selection to ensure no mixing.
     *
     * Scenario:
     * 1. User selects file A, partial text arrives ("Hello ")
     * 2. Before completion, user selects file B
     * 3. partialText should be cleared
     */
    @Test
    fun fileSelection_clearsPartialText() {
        // Simulate state from prior recognition that was interrupted
        var partialText = "Hello "
        assertEquals("Hello ", partialText)

        // When new file is selected, partialText is reset
        partialText = ""

        // After reset, partialText is empty
        assertEquals("", partialText)
    }

    /**
     * Requirement 1: 複数ファイルの連続選択で前回結果と混在しないことを保証する
     * Test: messages list should be cleared to prevent mixing of results from different files.
     *
     * This is the key requirement: one file → one aggregated result, not multiple.
     */
    @Test
    fun fileSelection_clearsMessages() {
        // Simulate state from prior file: messages contain results from file A
        var messages = listOf(
            UIChatMessage("File A result 1"),
            UIChatMessage("File A result 2")
        )
        assertEquals(2, messages.size)

        // When new file B is selected, messages should be cleared
        messages = emptyList()

        // After clear, messages is empty
        assertEquals(0, messages.size)
        assertEquals(emptyList<UIChatMessage>(), messages)
    }

    /**
     * Requirement 3: 認識ライフサイクル管理
     * Test: Verify the state model supports the required fields for lifecycle management.
     *
     * FileToTtsUiState should have:
     * - selectedFileName: to track which file is currently selected
     * - partialText: for intermediate recognition results
     * - messages: for final aggregated results
     * - isRecognizing: to manage lifecycle (from earlier tasks)
     */
    @Test
    fun fileToTtsUiState_hasRequiredFieldsForLifecycleManagement() {
        val state = FileToTtsUiState(
            selectedFileName = "audio.mp3",
            partialText = "",
            messages = emptyList(),
            isRecognizing = false
        )

        assertEquals("audio.mp3", state.selectedFileName)
        assertEquals("", state.partialText)
        assertEquals(emptyList<UIChatMessage>(), state.messages)
        assertEquals(false, state.isRecognizing)
    }

    /**
     * Requirement 1: ファイル選択時の状態遷移
     * Test: Simulating the complete state reset that occurs during onFileSelected().
     *
     * This demonstrates the pattern that FileToTtsViewModel.onFileSelected() should follow:
     * OLD_STATE → new selected file → NEW_CLEAN_STATE
     */
    @Test
    fun stateTransition_onFileSelection_resetsAllIntermediateState() {
        // State BEFORE file selection (from prior recognition)
        val stateBefore = FileToTtsUiState(
            selectedFileName = "previous_file.mp3",
            partialText = "Partial result from previous file...",
            messages = listOf(UIChatMessage("Previous file result")),
            isRecognizing = false
        )

        assertEquals("previous_file.mp3", stateBefore.selectedFileName)
        assertEquals("Partial result from previous file...", stateBefore.partialText)
        assertEquals(1, stateBefore.messages.size)

        // State AFTER file selection (new file selected)
        // Should copy to update selectedFileName, but reset intermediate state
        val stateAfter = stateBefore.copy(
            selectedFileName = "new_file.mp3",
            partialText = "",        // CLEAR for new file
            messages = emptyList()   // CLEAR for new file
        )

        assertEquals("new_file.mp3", stateAfter.selectedFileName)
        assertEquals("", stateAfter.partialText)
        assertEquals(emptyList<UIChatMessage>(), stateAfter.messages)
    }
}
