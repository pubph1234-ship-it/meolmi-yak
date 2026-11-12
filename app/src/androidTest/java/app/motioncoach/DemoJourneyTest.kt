package app.motioncoach

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a non-automotive Android emulator; Automotive additionally requires P gear/UX state. */
@RunWith(AndroidJUnit4::class)
class DemoJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun demoFeedbackAppearsInFinalReport() {
        compose.onNodeWithTag("start-demo").performScrollTo().performClick()
        compose.onNodeWithText("조금 불편해요").performScrollTo().performClick()
        compose.onNodeWithText("종료 · 리포트").performScrollTo().performClick()
        compose.onNodeWithTag("driver-goal").assertExists()
        compose.onNodeWithText("조금 불편해요").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("시연 · 가상 데이터").assertIsDisplayed()
    }
    @Test fun comparisonScenarioProducesReviewAndRetainsItsLabel() {
        compose.onNodeWithTag("style-SMOOTH").performScrollTo().performClick()
        compose.onNodeWithTag("comparison").performScrollTo().performClick()
        compose.onNodeWithTag("start-demo").performScrollTo().performClick()
        compose.onNodeWithText("일시정지").performScrollTo().performClick()
        compose.onNodeWithText("조금 불편해요").performScrollTo().assertIsNotEnabled()
        repeat(9) { compose.onNodeWithTag("skip").performScrollTo().performClick() }
        compose.onNodeWithText("종료 · 리포트").performScrollTo().performClick()
        compose.onNodeWithTag("driver-goal").assertTextContains("확인되지", substring = true)
        compose.onNodeWithTag("feedback-review").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("미리 준비한 운전 · 가상 체감 자동 입력").performScrollTo().assertIsDisplayed()
    }
}
