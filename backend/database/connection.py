"""
Firebase and Cloud Firestore Connection Manager
Initializes Firebase Admin SDK securely and provides a reusable Firestore client.
Gracefully handles missing or unconfigured credentials.
"""

import os
import logging
from pathlib import Path
from typing import Optional, Any
from backend.config import settings

logger = logging.getLogger("scam_shield.database")

# Lazy-loaded Firebase modules to ensure backend runs even before dependencies are installed
_firebase_imported = False
try:
    import firebase_admin
    from firebase_admin import credentials, firestore, auth
    _firebase_imported = True
except ImportError:
    firebase_admin = None
    credentials = None
    firestore = None
    auth = None
    logger.warning("firebase-admin package is not installed. Cloud Firestore will be unavailable.")

_firebase_app: Optional[Any] = None
_firestore_client: Optional[Any] = None
_mock_firestore_client: Optional[Any] = None
_is_initialized: bool = False


def set_firestore_client(mock_client: Optional[Any]) -> None:
    """Explicitly set a mock Firestore client for automated testing and offline verification."""
    global _mock_firestore_client
    _mock_firestore_client = mock_client


def initialize_firebase() -> bool:
    """
    Initialize Firebase Admin SDK using service account credentials.
    Returns True if successfully initialized, False otherwise.
    Safe to call multiple times; will not re-initialize an already active app.
    """
    global _firebase_app, _firestore_client, _is_initialized, _mock_firestore_client

    if _mock_firestore_client is not None:
        return True

    if _is_initialized and _firestore_client is not None:
        return True

    # If test mode is active, do not attempt remote network connections
    if os.environ.get("TESTING") == "true":
        logger.info("TESTING environment flag active: Skipping remote Firebase connection.")
        return False

    if not _firebase_imported:
        logger.warning("Cannot initialize Firebase: 'firebase-admin' library not installed.")
        return False

    # Check if Firebase is already initialized globally
    if firebase_admin._apps:
        _firebase_app = firebase_admin.get_app()
        try:
            _firestore_client = firestore.client()
            _is_initialized = True
            logger.info("Reused existing Firebase app instance.")
            return True
        except Exception as e:
            logger.error(f"Failed to acquire Firestore client from existing app: {e}")
            return False

    # 1. Attempt initialization from raw JSON environment variable (convenient for cloud deployment)
    raw_json = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON") or getattr(settings, "FIREBASE_SERVICE_ACCOUNT_JSON", None)
    if raw_json and raw_json.strip():
        try:
            import json
            cert_dict = json.loads(raw_json.strip())
            cred = credentials.Certificate(cert_dict)
            _firebase_app = firebase_admin.initialize_app(cred)
            _firestore_client = firestore.client()
            _is_initialized = True
            logger.info("Successfully connected to Cloud Firestore using FIREBASE_SERVICE_ACCOUNT_JSON environment variable.")
            return True
        except Exception as exc:
            logger.error(f"Error initializing Firebase from FIREBASE_SERVICE_ACCOUNT_JSON: {exc}")

    # 2. Resolve credential file path
    cred_path = settings.firebase_credential_path
    if not cred_path or not cred_path.exists():
        logger.warning(
            f"Firebase service account file not found at '{cred_path}'. "
            "Firestore persistence will remain disabled until valid credentials are added."
        )
        return False

    try:
        cred = credentials.Certificate(str(cred_path))
        _firebase_app = firebase_admin.initialize_app(cred)
        _firestore_client = firestore.client()
        _is_initialized = True
        logger.info(f"Successfully connected to Cloud Firestore using credentials at: {cred_path.name}")
        return True
    except Exception as exc:
        logger.error(f"Error initializing Firebase Admin SDK: {exc}")
        _is_initialized = False
        _firestore_client = None
        return False


def get_firestore_client() -> Optional[Any]:
    """
    Get the active Cloud Firestore client.
    Returns None if Firebase is not configured or unavailable.
    """
    global _firestore_client, _mock_firestore_client
    if _mock_firestore_client is not None:
        return _mock_firestore_client
    if _firestore_client is None:
        initialize_firebase()
    return _firestore_client


def is_firebase_available() -> bool:
    """Check whether Firestore is connected and ready for database operations."""
    global _mock_firestore_client
    if _mock_firestore_client is not None:
        return True
    return get_firestore_client() is not None


def get_firebase_app() -> Optional[Any]:
    """Get the active Firebase App instance."""
    global _firebase_app
    if _firebase_app is None:
        initialize_firebase()
    return _firebase_app
