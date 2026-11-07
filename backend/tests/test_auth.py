"""
Tests for Authentication, Token Verification, and Access Isolation
"""

from unittest.mock import MagicMock
from starlette.testclient import TestClient
import backend.utils.security as sec


def test_missing_auth_header_is_rejected(client: TestClient):
    """Ensure that calling protected endpoint without Authorization header fails with 401."""
    response = client.get("/api/auth/me")
    assert response.status_code == 401
    assert "Authentication required" in response.json()["error"]["message"]


def test_missing_firebase_credentials_handling(client: TestClient, monkeypatch):
    """
    Ensure that when Firebase is unconfigured, requesting verification handles it
    gracefully with 503 Service Unavailable.
    """
    monkeypatch.setattr(sec, "get_firebase_app", lambda: None)
    monkeypatch.setattr(sec, "fb_auth", None)

    headers = {"Authorization": "Bearer any_token"}
    response = client.get("/api/auth/me", headers=headers)
    assert response.status_code == 503
    assert "not configured" in response.json()["error"]["message"].lower()


def test_invalid_bearer_token_is_rejected(client: TestClient, monkeypatch):
    """Ensure that fraudulent or corrupt tokens are rejected with 401 when auth service is active."""
    # Mock active Firebase Auth instance whose verify_id_token raises an exception
    mock_auth = MagicMock()
    mock_auth.verify_id_token.side_effect = Exception("Malformed JWT token")

    monkeypatch.setattr(sec, "get_firebase_app", lambda: MagicMock())
    monkeypatch.setattr(sec, "fb_auth", mock_auth)

    headers = {"Authorization": "Bearer invalid_garbage_token_12345"}
    response = client.get("/api/auth/me", headers=headers)
    assert response.status_code == 401
    assert "Verification failed" in response.json()["error"]["message"]


def test_protected_history_requires_authentication(client: TestClient):
    """Ensure history endpoint cannot be accessed without authenticated identity."""
    response = client.get("/api/history")
    assert response.status_code == 401


def test_protected_contacts_requires_authentication(client: TestClient):
    """Ensure contacts cannot be accessed without authenticated identity."""
    response = client.get("/api/contacts")
    assert response.status_code == 401


def test_dev_auth_token_resolves_verified_identity(client: TestClient, auth_headers):
    """Ensure that when dev auth bypass is active, token resolves into structured UserProfile."""
    response = client.get("/api/auth/me", headers=auth_headers)
    assert response.status_code == 200
    data = response.json()
    assert data["uid"] == "dev_user_testuser123"
    assert "email" in data
