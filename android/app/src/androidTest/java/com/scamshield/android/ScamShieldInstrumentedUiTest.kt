package com.scamshield.android

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.domain.model.ScamCategory
import com.scamshield.android.ui.components.HeuristicDisclaimerBanner
import com.scamshield.android.ui.components.PatternBadgeFlow
import com.scamshield.android.ui.components.RiskScoreCard
import com.scamshield.android.ui.theme.ScamShieldTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScamShieldInstrumentedUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun analysisComponentsDisplayHighRiskGuidance() {
        composeTestRule.setContent {
            ScamShieldTheme {
                Column {
                    RiskScoreCard(score = 74, riskLevel = RiskLevel.HIGH)
                    PatternBadgeFlow(
                        patterns = listOf(
                            ScamCategory.CREDENTIAL_HARVESTING.apiKey,
                            ScamCategory.URGENCY.apiKey
                        )
                    )
                    HeuristicDisclaimerBanner(disclaimer = "Indicator only.")
                }
            }
        }

        composeTestRule.onNodeWithText("High Risk").assertIsDisplayed()
        composeTestRule.onNodeWithText("74%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Credential Harvesting").assertIsDisplayed()
        composeTestRule.onNodeWithText("Urgency & Pressure").assertIsDisplayed()
    }
}

