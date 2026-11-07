
"""
Scam Detection Service

Implements:
1. Rule-based baseline scam detection.
2. Optional machine learning model integration.
3. Context-aware credential detection to reduce false positives.
"""

import re
import logging
from typing import Dict, Any, List
from pathlib import Path

from backend.config import settings
from backend.services.preprocessing_service import preprocessing_service
from backend.models.ml_interface import ml_adapter


logger = logging.getLogger("scam_shield.scam_detection")


class ScamDetectionService:
    """
    Dual-engine scam detector:
    1. Transparent Rule-Based Baseline Engine.
    2. Optional Trained Machine Learning Adapter.
    """

    SUSPICIOUS_PATTERNS = {
        "authority_impersonation": {
            "weight": 25,
            "description": "Impersonation of law enforcement, government officials, or financial regulators",
            "keywords": [
                r"\bpolice\b", r"\bcbi\b", r"\bfbi\b",
                r"\bcustoms department\b", r"\bcyber crime\b",
                r"\bcyber cell\b", r"\benforcement directorate\b",
                r"\bed officer\b", r"\bsupreme court\b",
                r"\bhigh court\b", r"\bmagistrate\b",
                r"\bfederal agent\b", r"\btelecom regulatory\b",
                r"\btrai\b", r"\brbi official\b",
                r"\breserve bank\b", r"\bincome tax department\b",
                r"\birs\b", r"\bnarcotics control\b", r"\binterpol\b"
            ],
        },

        "threats_intimidation": {
            "weight": 25,
            "description": "Intimidation involving arrest, warrants, or legal penalties",
            "keywords": [
                r"\barrest warrant\b", r"\bwill be arrested\b",
                r"\bsend police\b", r"\bpolice are coming\b",
                r"\bfile an? fir\b", r"\blodge fir\b",
                r"\bcourt summons\b", r"\basset seizure\b",
                r"\bconfiscate\b", r"\bpassport cancel\b",
                r"\bdeportation\b", r"\bjail\b",
                r"\blegal action\b", r"\bimmediate prosecution\b",
                r"\bpenal code\b"
            ],
        },

        "urgency": {
            "weight": 20,
            "description": "High psychological pressure requiring an instant reaction",
            "keywords": [
                r"\bimmediately\b", r"\bright now\b",
                r"\bhurry\b", r"\burgent\b",
                r"\bwithin \d+ minutes?\b",
                r"\bwithin \d+ hours?\b",
                r"\bdo not delay\b",
                r"\bbefore it is too late\b",
                r"\btime is running out\b",
                r"\binstant action\b", r"\bact fast\b",
                r"\blast warning\b", r"\bfinal notice\b"
            ],
        },

        "credential_harvesting": {
            "weight": 30,
            "description": "Solicitation of private security credentials or verification codes",
            "keywords": [
                r"\botp\b",
                r"\bone[\s-]time password\b",
                r"\bpin\b",
                r"\bcvv\b",
                r"\bnet banking password\b",
                r"\blogin password\b",
                r"\batm pin\b",
                r"\bsecurity code\b",
                r"\bverification code\b",
                r"\bshare the code\b",
                r"\bread the code\b",
                r"\b2fa code\b",
                r"\btwo[\s-]factor code\b"
            ],
        },

        "account_suspension": {
            "weight": 20,
            "description": "Claims of blocked accounts, expired KYC, or disabled telephone lines",
            "keywords": [
                r"\baccount (?:has been |is )?frozen\b",
                r"\baccount (?:has been |is )?suspended\b",
                r"\baccount blocked\b",
                r"\bkyc (?:is )?expired\b",
                r"\bupdate (?:your )?kyc\b",
                r"\bdebit card blocked\b",
                r"\bsim card (?:will be )?disconnected\b",
                r"\bunauthorized transaction\b",
                r"\bsuspicious activity on your account\b"
            ],
        },

        "secrecy_isolation": {
            "weight": 25,
            "description": "Demands to isolate victims and conceal calls",
            "keywords": [
                r"\bdo not tell (?:anyone|anybody)\b",
                r"\bkeep this (?:secret|confidential)\b",
                r"\bdo not hang up\b",
                r"\bstay on the (?:line|call)\b",
                r"\bdo not inform your family\b",
                r"\bsecret investigation\b",
                r"\bgo to a private room\b",
                r"\bisolate yourself\b",
                r"\bnational security\b"
            ],
        },

        "fear_manipulation": {
            "weight": 20,
            "description": "Fabricated allegations involving criminal parcels or contraband",
            "keywords": [
                r"\billegal parcel\b",
                r"\bdrugs found\b",
                r"\bcourier seized\b",
                r"\bmoney laundering\b",
                r"\bterror funding\b",
                r"\bcontraband\b",
                r"\bidentity theft\b",
                r"\byour name is involved\b",
                r"\bcriminal investigation\b"
            ],
        },

        "financial_pressure": {
            "weight": 25,
            "description": "Demands for urgent payments, deposits, or gift cards",
            "keywords": [
                r"\btransfer money\b",
                r"\bsend money\b",
                r"\bsecurity deposit\b",
                r"\bpenalty fee\b",
                r"\bliquid funds\b",
                r"\bwire transfer\b",
                r"\brbi (?:safe|verification) account\b",
                r"\bgift cards?\b",
                r"\bcrypto\b",
                r"\bbitcoin\b",
                r"\bpay immediately\b",
                r"\brefundable deposit\b"
            ],
        },
    }

    def __init__(self):
        """Compile detection patterns and initialize the optional ML adapter."""

        self._compiled_patterns = {}

        for category, details in self.SUSPICIOUS_PATTERNS.items():
            combined = "|".join(
                f"(?:{pattern})"
                for pattern in details["keywords"]
            )

            self._compiled_patterns[category] = {
                "regex": re.compile(combined, re.IGNORECASE),
                "weight": details["weight"],
                "description": details["description"],
            }

        if settings.ML_MODEL_ENABLED:
            model_path = Path(settings.ML_MODEL_PATH)

            vec_path = (
                Path(settings.ML_VECTORIZER_PATH)
                if settings.ML_VECTORIZER_PATH
                else None
            )

            ml_adapter.load(model_path, vec_path)

    def analyze_patterns_baseline(
        self,
        text: str
    ) -> Dict[str, Any]:
        """
        Analyze text using rule-based patterns.

        Protective credential warnings are excluded from credential
        harvesting detection, while separate suspicious requests remain
        detectable.
        """

        if not text:
            return {
                "detected_patterns": [],
                "matched_snippets": {},
                "raw_score": 0,
                "engine": "rule_based_baseline",
            }

        normalized = preprocessing_service.prepare_for_inference(text)

        detected_patterns: List[str] = []
        matched_snippets: Dict[str, List[str]] = {}
        raw_score = 0

        # Split transcript into individual sentences.
        sentences = re.split(
            r"(?<=[.!?;])\s+|\n+",
            text
        )

        # Detect protective advice.
        protective_pattern = re.compile(
            r"\b(?:do not|don't|never)\s+"
            r"(?:share|disclose|give|provide|reveal|tell|send)\b"
            r"|\b(?:banks?|financial institutions?)\s+will never ask for\b",
            re.IGNORECASE,
        )

        for category, info in self._compiled_patterns.items():

            if category == "credential_harvesting":
                matches = []

                for sentence in sentences:

                    # Skip protective advice only.
                    if protective_pattern.search(sentence):
                        continue

                    for match in info["regex"].finditer(sentence):
                        matches.append(match.group(0))

            else:
                matches = [
                    match.group(0)
                    for match in info["regex"].finditer(normalized)
                ]

            # Remove duplicates while preserving order.
            matches = list(dict.fromkeys(matches))

            if matches:
                detected_patterns.append(category)
                matched_snippets[category] = matches
                raw_score += info["weight"]

        # Compounding scam tactic boosts.
        if (
            "credential_harvesting" in detected_patterns
            and "urgency" in detected_patterns
        ):
            raw_score += 15

        if (
            "authority_impersonation" in detected_patterns
            and "threats_intimidation" in detected_patterns
        ):
            raw_score += 15

        if (
            "secrecy_isolation" in detected_patterns
            and "financial_pressure" in detected_patterns
        ):
            raw_score += 15

        return {
            "detected_patterns": detected_patterns,
            "matched_snippets": matched_snippets,
            "raw_score": raw_score,
            "engine": "rule_based_baseline",
        }

    def detect(self, raw_text: str) -> Dict[str, Any]:
        """
        Execute the full detection workflow.

        Combines rule-based detection with optional ML predictions.
        """

        preprocessed = preprocessing_service.process(raw_text)

        rule_result = self.analyze_patterns_baseline(
            preprocessed["cleaned_text"]
        )

        ml_result = None

        if settings.ML_MODEL_ENABLED and ml_adapter.is_ready():
            ml_result = ml_adapter.predict(
                preprocessed["ml_ready_text"]
            )

        return {
            "transcript": preprocessed["cleaned_text"],
            "preprocessed": preprocessed,
            "rule_result": rule_result,
            "ml_result": ml_result,
            "detected_patterns": rule_result["detected_patterns"],
        }


# Shared service instance.
scam_detection_service = ScamDetectionService()
