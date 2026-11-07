"""
Tests for Preprocessing and Rule-Based Baseline Scam Detection
"""

from backend.services.preprocessing_service import preprocessing_service
from backend.services.scam_detection_service import scam_detection_service
from backend.services.risk_scoring_service import risk_scoring_service


def test_preprocessing_normalizes_whitespace_and_contractions():
    """Ensure that preprocessing collapses extra spaces and expands standard contractions."""
    raw = "  Hello   there,\n\nI  don't   know  what's   happening...   "
    processed = preprocessing_service.process(raw)
    assert "  " not in processed["cleaned_text"]
    assert "do not" in processed["ml_ready_text"]
    assert "what is" in processed["ml_ready_text"]


def test_rule_based_detection_authority_impersonation():
    """Verify detection of fake police and cyber crime departments."""
    text = "This is inspector Sharma from Delhi Cyber Crime Police Department."
    result = scam_detection_service.detect(text)
    assert "authority_impersonation" in result["detected_patterns"]


def test_rule_based_detection_credential_harvesting():
    """Verify detection of attempts to steal OTP and banking passwords."""
    text = "We need you to read the code and share your OTP and net banking password."
    result = scam_detection_service.detect(text)
    assert "credential_harvesting" in result["detected_patterns"]


def test_rule_based_detection_urgency_and_intimidation():
    """Verify detection of urgent threats of arrest and warrants."""
    text = "An arrest warrant has been issued. You will be arrested immediately within 10 minutes!"
    result = scam_detection_service.detect(text)
    assert "threats_intimidation" in result["detected_patterns"]
    assert "urgency" in result["detected_patterns"]


def test_rule_based_detection_financial_pressure_and_secrecy():
    """Verify detection of demands for money transfer combined with secrecy demands."""
    text = "Transfer money to the RBI safe account right now. Do not tell anyone and stay on the call."
    result = scam_detection_service.detect(text)
    assert "financial_pressure" in result["detected_patterns"]
    assert "secrecy_isolation" in result["detected_patterns"]


def test_benign_conversation_is_classified_as_low_risk():
    """Ensure that legitimate everyday conversation is not falsely flagged as high scam."""
    text = "Hey John, are we still meeting for lunch tomorrow at 1 PM? Let me know if you want Italian."
    result = scam_detection_service.detect(text)
    evaluation = risk_scoring_service.evaluate(result)
    assert evaluation["risk_score"] == 0
    assert evaluation["risk_level"] == "LOW"
    assert len(evaluation["detected_patterns"]) == 0


def test_compounding_scam_tactics_reach_critical_risk():
    """Verify that multiple compounding scam tactics (authority + threat + urgency + OTP + transfer) reach CRITICAL severity."""
    text = (
        "This is the CBI Police officer. An illegal parcel with drugs was seized under your name. "
        "An arrest warrant is issued. Do not tell anyone! Transfer money immediately to verify innocence, "
        "and read out your OTP right now!"
    )
    result = scam_detection_service.detect(text)
    evaluation = risk_scoring_service.evaluate(result)
    assert evaluation["risk_score"] >= 85
    assert evaluation["risk_level"] == "CRITICAL"
    assert "authority_impersonation" in evaluation["detected_patterns"]
    assert "credential_harvesting" in evaluation["detected_patterns"]
    assert "urgency" in evaluation["detected_patterns"]
    # Check that safety recommendations are produced
    assert len(evaluation["recommendations"]) > 0
    assert any("OTP" in r for r in evaluation["recommendations"])



def test_protective_otp_warning_is_not_credential_harvesting():
    """Protective advice must not be mistaken for an attempt to steal credentials."""
    text = (
        "Do not share your OTP with anyone. "
        "Never disclose your PIN. "
        "Banks will never ask for your OTP."
    )
    result = scam_detection_service.detect(text)
    assert "credential_harvesting" not in result["detected_patterns"]


def test_direct_otp_request_is_detected():
    """A direct request for an OTP must still be detected."""
    text = "Share your OTP immediately."
    result = scam_detection_service.detect(text)
    assert "credential_harvesting" in result["detected_patterns"]


def test_protective_warning_and_real_otp_request_are_detected():
    """A protective warning must not hide a separate malicious OTP request."""
    text = (
        "Never share your OTP with anyone. "
        "I need you to share your OTP immediately to verify your account."
    )
    result = scam_detection_service.detect(text)
    assert "credential_harvesting" in result["detected_patterns"]
