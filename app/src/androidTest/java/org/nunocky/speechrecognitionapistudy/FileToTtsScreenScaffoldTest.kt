package org.nunocky.speechrecognitionapistudy

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.nunocky.speechrecognitionapistudy.ui.file.FileToTtsScreen

/**
 * Compose UI tests for Task 1.1 — FileToTtsScreen scaffold and back navigation.
 * Requirements: 6.1, 6.2
 */
class FileToTtsScreenScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Requirement 6.1: The screen shall display a back-arrow navigation icon in the TopAppBar.
     */
    @Test
    fun topAppBar_displaysBackArrowIcon() {
        composeTestRule.setContent {
            FileToTtsScreen(onBack = {})
        }

        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
    }

    /**
     * Requirement 6.2: When the user taps the back-arrow icon, the screen shall invoke the
     * onBack callback.
     */
    @Test
    fun backArrowButton_onClick_invokesOnBackCallback() {
        var onBackCalled = false

        composeTestRule.setContent {
            FileToTtsScreen(onBack = { onBackCalled = true })
        }

        composeTestRule
            .onNodeWithContentDescription("Back")
            .performClick()

        assertTrue("onBack callback should be invoked when back arrow is tapped", onBackCalled)
    }

    /**
     * Requirement 6.1: TopAppBar title should be visible.
     */
    @Test
    fun topAppBar_displaysTitle() {
        composeTestRule.setContent {
            FileToTtsScreen(onBack = {})
        }

        composeTestRule
            .onNodeWithText("ファイルからTTS")
            .assertIsDisplayed()
    }
}

