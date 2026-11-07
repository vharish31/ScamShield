"""
Tests for Audio Upload Validation and Storage Lifecycle
"""

import io
from starlette.testclient import TestClient


def test_upload_requires_authentication(client: TestClient):
    """Ensure that unauthenticated uploads are rejected with 401."""
    files = {"file": ("malicious.exe", b"MZ\x90\x00executable_bytes", "application/octet-stream")}
    response = client.post("/api/audio/upload", files=files)
    assert response.status_code == 401


def test_upload_invalid_extension(client: TestClient, auth_headers):
    """Ensure that non-whitelisted extensions are immediately rejected."""
    files = {"file": ("malicious.exe", b"MZ\x90\x00executable_bytes", "application/octet-stream")}
    response = client.post("/api/audio/upload", files=files, headers=auth_headers)
    assert response.status_code == 400
    data = response.json()
    assert "Unsupported audio format" in data["error"]["message"]


def test_upload_empty_audio_file(client: TestClient, auth_headers):
    """Ensure that zero-byte files are rejected."""
    files = {"file": ("empty.wav", b"", "audio/wav")}
    response = client.post("/api/audio/upload", files=files, headers=auth_headers)
    assert response.status_code == 400
    data = response.json()
    assert "empty" in data["error"]["message"].lower()


def test_upload_spoofed_audio_extension(client: TestClient, auth_headers):
    """Ensure that renaming an arbitrary text/script file to .wav fails magic byte verification."""
    fake_audio_content = b"This is just a plain text document pretending to be audio."
    files = {"file": ("fake_voice.wav", fake_audio_content, "audio/wav")}
    response = client.post("/api/audio/upload", files=files, headers=auth_headers)
    assert response.status_code == 400
    data = response.json()
    assert "Invalid audio content" in data["error"]["message"]


def test_upload_valid_wav_audio(client: TestClient, sample_wav_bytes, auth_headers):
    """Ensure that a valid audio file with matching magic bytes is accepted and stored."""
    files = {"file": ("recording.wav", sample_wav_bytes, "audio/wav")}
    response = client.post("/api/audio/upload", files=files, headers=auth_headers)
    assert response.status_code == 201
    data = response.json()
    assert "audio_id" in data
    assert data["format"] == "wav"
    assert data["status"] == "uploaded"
    assert data["file_size_bytes"] == len(sample_wav_bytes)

    # Verify audio status endpoint
    audio_id = data["audio_id"]
    status_resp = client.get(f"/api/audio/{audio_id}", headers=auth_headers)
    assert status_resp.status_code == 200
    assert status_resp.json()["status"] == "ready_for_analysis"

    # Clean up
    del_resp = client.delete(f"/api/audio/{audio_id}", headers=auth_headers)
    assert del_resp.status_code == 200


def test_upload_valid_mp3_audio(client: TestClient, sample_mp3_bytes, auth_headers):
    """Ensure that valid MP3 ID3 container bytes are accepted."""
    files = {"file": ("call_sample.mp3", sample_mp3_bytes, "audio/mpeg")}
    response = client.post("/api/audio/upload", files=files, headers=auth_headers)
    assert response.status_code == 201
    data = response.json()
    assert data["format"] == "mp3"
    assert "audio_id" in data

    # Clean up
    del_resp = client.delete(f"/api/audio/{data['audio_id']}", headers=auth_headers)
    assert del_resp.status_code == 200
