"""
Tests for Analysis API Endpoint Execution and Schema Conformance
"""

from starlette.testclient import TestClient


def test_analyze_text_schema_conformance(client: TestClient):
    """
    Verify that /api/analyze/text strictly returns the JSON format requested in specification:
    - analysis_id
    - transcript
    - risk_score
    - risk_level
    - detected_patterns
    - recommendations
    """
    payload = {
        "text": "Transfer money immediately to avoid arrest. Share your OTP right now."
    }
    response = client.post("/api/analyze/text", json=payload)
    assert response.status_code == 200
    data = response.json()

    # Core spec fields
    assert "analysis_id" in data
    assert "transcript" in data
    assert "risk_score" in data
    assert "risk_level" in data
    assert "detected_patterns" in data
    assert "recommendations" in data

    # Type & Boundary validations
    assert isinstance(data["analysis_id"], str)
    assert isinstance(data["risk_score"], int)
    assert 0 <= data["risk_score"] <= 100
    assert data["risk_level"] in ("LOW", "MEDIUM", "HIGH", "CRITICAL")
    assert isinstance(data["detected_patterns"], list)
    assert isinstance(data["recommendations"], list)

    # Content validations
    assert "urgency" in data["detected_patterns"]
    assert "credential_harvesting" in data["detected_patterns"]
    assert "financial_pressure" in data["detected_patterns"]
    assert len(data["recommendations"]) >= 1


def test_analyze_text_empty_input_rejected(client: TestClient):
    """Ensure that empty string input is rejected."""
    response = client.post("/api/analyze/text", json={"text": "   "})
    assert response.status_code == 400


def test_analyze_audio_requires_authentication(client: TestClient):
    """Ensure that calling /api/analyze/audio without auth header fails with 401."""
    response = client.post("/api/analyze/audio")
    assert response.status_code == 401


def test_analyze_audio_missing_both_file_and_id(client: TestClient, auth_headers):
    """Ensure that calling /api/analyze/audio without audio_id or file returns 400."""
    response = client.post("/api/analyze/audio", headers=auth_headers)
    assert response.status_code == 400
    assert "audio_id" in response.json()["error"]["message"]


def test_analyze_audio_non_existent_id(client: TestClient, auth_headers):
    """Ensure that supplying an unknown audio_id returns 404."""
    response = client.post(
        "/api/analyze/audio",
        data={"audio_id": "00000000-0000-0000-0000-000000000000"},
        headers=auth_headers,
    )
    assert response.status_code == 404
