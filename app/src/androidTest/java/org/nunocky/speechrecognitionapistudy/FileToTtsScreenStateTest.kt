package org.nunocky.speechrecognitionapistudy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.nunocky.speechrecognitionapistudy.ui.component.UIChatMessage
import org.nunocky.speechrecognitionapistudy.ui.file.FileToTtsScreenContent
import org.nunocky.speechrecognitionapistudy.ui.file.FileToTtsScreen
import org.nunocky.speechrecognitionapistudy.ui.file.FileToTtsUiState

/**
 * Compose UI integration tests for FileToTtsScreen — Tasks 7.1 (state/guard behavior surfaced
 * through UI) and 7.2 (key acceptance criteria).
 *
 * Requirements: 1.2, 1.4, 2.2, 2.4, 2.5, 3.3, 3.5, 5.2, 5.3, 6.1, 6.2
 */
class FileToTtsScreenStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun recognizingState_showsOnlyPartialText_andHidesMessages() {
        composeTestRule.setContent {
            FileToTtsScreenContent(
                uiState = FileToTtsUiState(
                    isRecognizing = true,
                    partialText = "intermediate transcript",
                    messages = listOf(UIChatMessage("aggregated final transcript"))
                ),
                onBack = {},
                onSelectFile = {},
                onStartRecognition = {},
                onTogglePlayback = {}
            )
        }

        composeTestRule.onNodeWithText("intermediate transcript").assertIsDisplayed()
        composeTestRule.onNodeWithText("aggregated final transcript").assertDoesNotExist()
    }

    @Test
    fun completedState_showsOnlyAggregatedMessages_andHidesPartialText() {
        composeTestRule.setContent {
            FileToTtsScreenContent(
                uiState = FileToTtsUiState(
                    isRecognizing = false,
                    partialText = "stale partial transcript",
                    messages = listOf(UIChatMessage("aggregated final transcript"))
                ),
                onBack = {},
                onSelectFile = {},
                onStartRecognition = {},
                onTogglePlayback = {}
            )
        }

        composeTestRule.onNodeWithText("aggregated final transcript").assertIsDisplayed()
        composeTestRule.onNodeWithText("stale partial transcript").assertDoesNotExist()
    }

    // ---- Initial state ----

    /**
     * Requirement 3.5: Placeholder text is shown when no results have been produced.
     */
    @Test
    fun initialState_showsResultPlaceholder() {
        composeTestRule.setContent { FileToTtsScreen(onBack = {}) }

        composeTestRule
            .onNodeWithText("認識結果がここに表示されます")
            .assertIsDisplayed()
    }

    /**
     * Requirements 1.2, 1.4: "No file selected" label displayed before any selection.
     */
    @Test
    fun initialState_showsNoFileSelectedLabel() {
        composeTestRule.setContent { FileToTtsScreen(onBack = {}) }

        composeTestRule
            .onNodeWithText("ファイルが選択されていません")
            .assertIsDisplayed()
    }

    /**
     * Requirement 2.4: "ファイルを選択" button is enabled in idle state.
     */
    @Test
    fun initialState_fileSelectButtonIsEnabled() {
        composeTestRule.setContent { FileToTtsScreen(onBack = {}) }

        composeTestRule
            .onNodeWithText("ファイルを選択")
            .assertIsEnabled()
    }

    /**
     * Requirement 2.4: "認識開始" button is enabled in idle state.
     */
    @Test
    fun initialState_startRecognitionButtonIsEnabled() {
        composeTestRule.setContent { FileToTtsScreen(onBack = {}) }

        composeTestRule
            .onNodeWithText("認識開始")
            .assertIsEnabled()
    }

    /**
     * Requirement 2.5: Processing indicator (CircularProgressIndicator) is NOT visible initially.
     */
    @Test
    fun initialState_progressIndicatorNotVisible() {
        composeTestRule.setContent { FileToTtsScreen(onBack = {}) }

        composeTestRule
            .onNodeWithTag("progress_indicator")
            .assertIsNotDisplayed()
    }

    // ---- No-file guard (Requirement 2.2 / Task 4.1) ----

    /**
     * Requirement 2.2 / 5.3: Tapping "認識開始" with no file selected leaves both buttons enabled
     * (recognition is NOT started — idempotency: isProcessing stays false).
     */
    @Test
    fun startRecognition_withNoFileSelected_buttonsRemainEnabled() {
        composeTestRule.setContent { FileToTtsScreen(onBack = {}) }

        composeTestRule
            .onNodeWithText("認識開始")
            .performClick()

        // Buttons must stay enabled — recognition must NOT have started
        composeTestRule.onNodeWithText("ファイルを選択").assertIsEnabled()
        composeTestRule.onNodeWithText("認識開始").assertIsEnabled()
    }

    /**
     * Requirement 2.2 / 2.5: Progress indicator is NOT shown when tapping "認識開始"
     * with no file selected (guard prevents isProcessing from being set to true).
     */
    @Test
    fun startRecognition_withNoFileSelected_progressIndicatorNotShown() {
        composeTestRule.setContent { FileToTtsScreen(onBack = {}) }

        composeTestRule.onNodeWithText("認識開始").performClick()

        composeTestRule
            .onNodeWithTag("progress_indicator")
            .assertIsNotDisplayed()
    }

    // ---- Navigation (Requirements 6.1, 6.2) ----

    /**
     * Requirement 6.1: Back-arrow icon is visible in the TopAppBar.
     */
    @Test
    fun topAppBar_backArrowIsDisplayed() {
        composeTestRule.setContent { FileToTtsScreen(onBack = {}) }

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
    }

    /**
     * Requirement 6.2: Tapping the back-arrow icon invokes the onBack callback.
     */
    @Test
    fun backArrow_onClick_invokesOnBackCallback() {
        var called = false
        composeTestRule.setContent { FileToTtsScreen(onBack = { called = true }) }

        composeTestRule
            .onNodeWithContentDescription("Back")
            .performClick()

        assertTrue("onBack should be invoked when back arrow is tapped", called)
    }

    /**
     * Requirement 5.2: Composable can be disposed (navigating back) without crash even when
     * no recognition is active.
     */
    @Test
    fun backNavigation_withNoActiveRecognition_doesNotCrash() {
        var backCalled = false
        composeTestRule.setContent { FileToTtsScreen(onBack = { backCalled = true }) }

        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backCalled)
        // If we reach here without an exception, disposal was clean
    }
}
