"""
Risk Scoring and Safety Advisory Service
Computes normalized risk scores (0-100), assigns severity tiers (LOW, MEDIUM, HIGH, CRITICAL),
generates actionable safety guidance, and provides legal/heuristic disclaimers.
"""

from typing import Dict, Any, List, Tuple


class RiskScoringService:
    """Calculates risk levels and contextual protective recommendations."""

    # Contextual safety advice mapped to detected manipulation tactics
    RECOMMENDATION_CATALOG = {
        "credential_harvesting": [
            "Never share your OTP, PIN, CVV, or banking passwords. Genuine banks and police officers will NEVER ask for verification codes.",
            "If you entered any credentials, immediately contact your bank to freeze your card and accounts.",
        ],
        "authority_impersonation": [
            "Government officials, CBI, and police do not conduct arrests or trials over WhatsApp or phone calls.",
            "Hang up immediately and independently verify by dialing your local police station or department helpline.",
        ],
        "urgency": [
            "Scammers deliberately create false urgency to force rash decisions. Take a breath and do not rush.",
            "Refuse to make immediate commitments while on the call.",
        ],
        "threats_intimidation": [
            "Do not be intimidated by threats of arrest or warrants over the phone.",
            "Legitimate legal summons are served in writing through official judicial channels, never via unsolicited phone calls.",
        ],
        "account_suspension": [
            "Do not click any SMS or chat links to update KYC or reactivate accounts.",
            "Verify your account status by visiting your bank's official website or local branch directly.",
        ],
        "secrecy_isolation": [
            "Scammers urge secrecy to isolate you from help. Discuss this call immediately with a trusted family member or friend.",
            "Never isolate yourself in a room or stay on continuous call under stranger instructions.",
        ],
        "fear_manipulation": [
            "Do not panic over claims of intercepted illegal parcels or contraband under your name.",
            "Report suspected cyber crime extortion to the official Cyber Crime Portal or call 1930.",
        ],
        "financial_pressure": [
            "Do not transfer money to any 'RBI safe account', 'verification account', or via gift cards/cryptocurrency.",
            "Any request to transfer funds to verify innocence is 100% fraudulent.",
        ],
    }

    # Baseline recommendations when no high-risk patterns are flagged
    DEFAULT_RECOMMENDATIONS = [
        "Remain vigilant when unknown callers ask about financial or personal matters.",
        "Always verify caller identities using official publicly listed telephone directories.",
    ]

    def calculate_score(self, detection_result: Dict[str, Any]) -> Tuple[int, str]:
        """
        Derive an integer risk score (0-100) and severity category.
        Blends rule-based heuristic score with ML probability if available.
        """
        rule_data = detection_result.get("rule_result", {})
        raw_score = rule_data.get("raw_score", 0)

        # Cap heuristic score between 0 and 100
        heuristic_score = min(100, max(0, raw_score))

        ml_result = detection_result.get("ml_result")
        if ml_result and ml_result.get("model_available"):
            # If a trained ML model is available, blend heuristic (40%) and ML (60%)
            ml_prob = ml_result.get("scam_probability", 0.0)
            ml_score = int(ml_prob * 100)
            final_score = int(0.4 * heuristic_score + 0.6 * ml_score)
        else:
            final_score = heuristic_score

        # Bound to 0-100
        final_score = min(100, max(0, final_score))

        # Severity categorization
        if final_score >= 85:
            level = "CRITICAL"
        elif final_score >= 60:
            level = "HIGH"
        elif final_score >= 30:
            level = "MEDIUM"
        else:
            level = "LOW"

        return final_score, level

    def generate_recommendations(self, detected_patterns: List[str], risk_level: str) -> List[str]:
        """Generate targeted safety recommendations based on observed behavioral markers."""
        recommendations: List[str] = []

        for pattern in detected_patterns:
            if pattern in self.RECOMMENDATION_CATALOG:
                # Add top recommendation for this pattern
                recommendations.append(self.RECOMMENDATION_CATALOG[pattern][0])

        if not recommendations:
            recommendations.extend(self.DEFAULT_RECOMMENDATIONS)
        elif risk_level in ("HIGH", "CRITICAL"):
            recommendations.append("Do not transfer money under any circumstances.")
            recommendations.append("Report this number to the National Cyber Crime Helpline (1930).")

        # Deduplicate while preserving order
        seen = set()
        unique_recs = []
        for r in recommendations:
            if r not in seen:
                seen.add(r)
                unique_recs.append(r)

        return unique_recs

    def evaluate(self, detection_result: Dict[str, Any]) -> Dict[str, Any]:
        """
        Compile complete risk assessment report.
        Strictly distinguishes heuristic indicators from proof of criminality.
        """
        detected_patterns = detection_result.get("detected_patterns", [])
        risk_score, risk_level = self.calculate_score(detection_result)
        recommendations = self.generate_recommendations(detected_patterns, risk_level)

        return {
            "risk_score": risk_score,
            "risk_level": risk_level,
            "detected_patterns": detected_patterns,
            "recommendations": recommendations,
            "is_heuristic": True,
            "confidence_disclaimer": (
                "The risk score is a heuristic assessment based on linguistic and behavioral markers; "
                "it does not constitute definitive proof that a caller is fraudulent. Always verify independently."
            ),
        }


risk_scoring_service = RiskScoringService()

