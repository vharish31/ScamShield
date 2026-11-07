"""
Pytest Fixtures and Mock Configurations for Scam Shield
Mocks external services (Firebase, Speech-to-Text) to allow tests to run offline without credentials.
"""

import sys
from pathlib import Path
from typing import Dict, Any, Generator
import pytest
from unittest.mock import MagicMock
from starlette.testclient import TestClient

# Ensure workspace root is in sys.path
WORKSPACE_ROOT = Path(__file__).resolve().parent.parent.parent
if str(WORKSPACE_ROOT) not in sys.path:
    sys.path.insert(0, str(WORKSPACE_ROOT))

# Enable dev auth bypass during tests
from backend.config import settings
settings.ALLOW_DEV_AUTH_BYPASS = True


# ==============================================================================
# In-Memory Mock Firestore for Offline Testing
# ==============================================================================

class MockDocumentSnapshot:
    def __init__(self, doc_id: str, data: Dict[str, Any] = None):
        self.id = doc_id
        self._data = data.copy() if data else {}
        self.exists = bool(data)

    def to_dict(self) -> Dict[str, Any]:
        return self._data.copy()

    def get(self, field_name: str, default=None):
        return self._data.get(field_name, default)


class MockDocumentReference:
    def __init__(self, doc_id: str, collection: "MockCollectionReference"):
        self.id = doc_id
        self._collection = collection

    def set(self, data: Dict[str, Any], merge: bool = False):
        if merge and self.id in self._collection._storage:
            self._collection._storage[self.id].update(data)
        else:
            self._collection._storage[self.id] = data.copy()

    def get(self) -> MockDocumentSnapshot:
        data = self._collection._storage.get(self.id)
        return MockDocumentSnapshot(self.id, data)

    def delete(self):
        if self.id in self._collection._storage:
            del self._collection._storage[self.id]


class MockQuery:
    def __init__(self, collection: "MockCollectionReference", field: str, op: str, value: Any):
        self._collection = collection
        self._field = field
        self._op = op
        self._value = value

    def stream(self) -> Generator[MockDocumentSnapshot, None, None]:
        for doc_id, data in list(self._collection._storage.items()):
            if self._op == "==" and data.get(self._field) == self._value:
                yield MockDocumentSnapshot(doc_id, data)


class MockCollectionReference:
    def __init__(self, name: str):
        self.name = name
        self._storage: Dict[str, Dict[str, Any]] = {}

    def document(self, doc_id: str = None) -> MockDocumentReference:
        if not doc_id:
            import uuid
            doc_id = str(uuid.uuid4())
        return MockDocumentReference(doc_id, self)

    def where(self, field: str, op: str, value: Any) -> MockQuery:
        return MockQuery(self, field, op, value)

    def stream(self) -> Generator[MockDocumentSnapshot, None, None]:
        for doc_id, data in list(self._storage.items()):
            yield MockDocumentSnapshot(doc_id, data)


class MockFirestoreClient:
    def __init__(self):
        self._collections: Dict[str, MockCollectionReference] = {}

    def collection(self, name: str) -> MockCollectionReference:
        if name not in self._collections:
            self._collections[name] = MockCollectionReference(name)
        return self._collections[name]


import os
os.environ["TESTING"] = "true"

from backend.database.connection import set_firestore_client

@pytest.fixture(autouse=True)
def mock_firestore():
    """
    Automatically configure in-memory Firestore client for all tests so no external GCP network calls occur.
    """
    mock_db = MockFirestoreClient()
    set_firestore_client(mock_db)
    yield mock_db
    set_firestore_client(None)


from backend.main import app


@pytest.fixture
def client():
    """Provides a synchronous TestClient for testing FastAPI endpoints."""
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def sample_wav_bytes():
    """
    Creates a minimal, valid PCM WAV file header (44 bytes).
    RIFF [size] WAVE fmt [16] [PCM=1] [Channels=1] [SampleRate=8000] [ByteRate=16000] [BlockAlign=2] [Bits=16] data [0]
    """
    riff = b"RIFF"
    file_size = (36).to_bytes(4, "little")
    wave = b"WAVE"
    fmt = b"fmt "
    fmt_size = (16).to_bytes(4, "little")
    audio_format = (1).to_bytes(2, "little")  # PCM
    channels = (1).to_bytes(2, "little")      # Mono
    sample_rate = (8000).to_bytes(4, "little")
    byte_rate = (16000).to_bytes(4, "little")
    block_align = (2).to_bytes(2, "little")
    bits_per_sample = (16).to_bytes(2, "little")
    data_header = b"data"
    data_size = (0).to_bytes(4, "little")

    return (
        riff + file_size + wave + fmt + fmt_size + audio_format +
        channels + sample_rate + byte_rate + block_align + bits_per_sample +
        data_header + data_size
    )


@pytest.fixture
def sample_mp3_bytes():
    """Returns valid ID3v2 tagged bytes representing minimal MP3 header."""
    return b"ID3\x03\x00\x00\x00\x00\x00\x0a" + b"\x00" * 20


@pytest.fixture
def auth_headers():
    """Returns test authorization headers recognized when dev auth bypass is active."""
    return {"Authorization": "Bearer dev_test_token_testuser123"}
