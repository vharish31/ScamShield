"""
Tests for Core API System Endpoints and Error Handling
"""

def test_root_endpoint(client):
    """Verify that root endpoint returns application metadata and operational status."""
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["app_name"] == "Scam Shield API"
    assert data["status"] == "running"
    assert "version" in data
    assert data["docs_url"] == "/docs"


def test_health_check_endpoint(client):
    """Verify that /health checks all subsystem states without throwing exceptions."""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert "firebase_connected" in data
    assert "whisper_configured" in data
    assert "ml_model_status" in data


def test_404_error_handling(client):
    """Verify standard structured error envelope for non-existent routes."""
    response = client.get("/api/non_existent_route")
    assert response.status_code == 404
    data = response.json()
    assert "error" in data
    assert data["error"]["code"] == "HTTP_404"
    assert "message" in data["error"]


def test_validation_error_handling(client):
    """Verify standard structured error envelope for malformed request payloads."""
    # Sending invalid data type to endpoint expecting specific structure
    response = client.post("/api/analyze/text", json={"wrong_key": 123})
    assert response.status_code == 422
    data = response.json()
    assert "error" in data
    assert data["error"]["code"] == "VALIDATION_ERROR"
    assert isinstance(data["error"]["details"], list)

