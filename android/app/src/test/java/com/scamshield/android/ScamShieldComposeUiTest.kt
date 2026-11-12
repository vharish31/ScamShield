package com.scamshield.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.scamshield.android.domain.model.RiskLevel
import com.scamshield.android.domain.model.ScamCategory
import com.scamshield.android.ui.components.EmptyStateCard
import com.scamshield.android.ui.components.HeuristicDisclaimerBanner
import com.scamshield.android.ui.components.PatternBadgeFlow
import com.scamshield.android.ui.components.RiskScoreCard
import com.scamshield.android.ui.theme.ScamShieldTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.foundation.layout.Column
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScamShieldComposeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `risk score card renders severity warning message and patterns`() {
        composeRule.setContent {
            ScamShieldTheme {
                Column {
                    RiskScoreCard(score = 92, riskLevel = RiskLevel.CRITICAL)
                    PatternBadgeFlow(
                        patterns = listOf(
                            ScamCategory.AUTHORITY_IMPERSONATION.apiKey,
                            ScamCategory.FINANCIAL_PRESSURE.apiKey
                        )
                    )
                    HeuristicDisclaimerBanner(
                        disclaimer = "AI risk score is an indicator, not proof of fraud."
                    )
                }
            }
        }

        composeRule.onNodeWithText("Critical Risk").assertIsDisplayed()
        composeRule.onNodeWithText("92%").assertIsDisplayed()
        composeRule.onNodeWithText(RiskLevel.CRITICAL.warningMessage).assertIsDisplayed()
        composeRule.onNodeWithText("Authority Impersonation").assertIsDisplayed()
        composeRule.onNodeWithText("Financial Pressure").assertIsDisplayed()
        composeRule.onNodeWithText("AI risk score is an indicator, not proof of fraud.").assertIsDisplayed()
    }

    @Test
    fun `empty state card renders guidance text and action button`() {
        composeRule.setContent {
            ScamShieldTheme(darkTheme = true) {
                EmptyStateCard(
                    icon = Icons.Filled.Shield,
                    title = "Contacts Permission Required",
                    message = "Saved-contact exclusion cannot be guaranteed without Contacts access.",
                    actionLabel = "Allow Contacts Access",
                    onAction = {}
                )
            }
        }

        composeRule.onNodeWithText("Contacts Permission Required").assertIsDisplayed()
        composeRule.onNodeWithText("Allow Contacts Access").assertIsDisplayed()
    }
}

