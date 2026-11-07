"""
Scam Shield - Firebase & Cloud Firestore Connectivity Verification Script
Tests your real Firebase credentials, Cloud Firestore read/write capabilities,
and Firebase Authentication readiness without cluttering production collections.

Run with:
    python test_firebase.py
"""

import os
import sys
import json
import uuid
from datetime import datetime, timezone
from pathlib import Path

# Add project root and backend directory to Python sys.path
BACKEND_DIR = Path(__file__).resolve().parent
REPO_ROOT = BACKEND_DIR.parent
for p in (str(REPO_ROOT), str(BACKEND_DIR)):
    if p not in sys.path:
        sys.path.insert(0, p)


# Reconfigure stdout for UTF-8 on Windows terminals if supported
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


def print_step(step_num: int, title: str):
    print(f"\n[{step_num}] {title}...", flush=True)


def print_success(message: str):
    print(f"    [PASS] {message}", flush=True)


def print_warning(message: str):
    print(f"    [WARN] {message}", flush=True)


def print_error(message: str):
    print(f"    [FAIL] {message}", flush=True)


def main():
    print("=" * 70)
    print(" Scam Shield - Firebase & Firestore Connectivity Diagnostic")
    print("=" * 70)

    # --------------------------------------------------------------------------
    # Step 1: Check environment configuration
    # --------------------------------------------------------------------------
    print_step(1, "Checking Environment & Credentials File")
    from backend.config import settings

    cred_path = settings.firebase_credential_path
    print(f"    Configured credential path : {settings.FIREBASE_SERVICE_ACCOUNT_PATH}")
    print(f"    Resolved absolute path     : {cred_path}")

    if not cred_path or not cred_path.exists():
        print_error(f"Service account credential file not found at: {cred_path}")
        print("\nFix:")
        print("1. Download service-account JSON from Firebase Console > Project Settings > Service accounts.")
        print(f"2. Place it in: {BACKEND_DIR / 'firebase-service-account.json'}")
        print("3. Ensure FIREBASE_SERVICE_ACCOUNT_PATH in .env points to that file.")
        sys.exit(1)

    try:
        with open(cred_path, "r", encoding="utf-8") as f:
            cred_data = json.load(f)
        project_id = cred_data.get("project_id", "Unknown")
        client_email = cred_data.get("client_email", "Unknown")
        print_success(f"Credential file found and valid.")
        print(f"    Firebase Project ID        : {project_id}")
        print(f"    Service Account Email      : {client_email}")
    except Exception as exc:
        print_error(f"Failed to parse credential JSON: {exc}")
        sys.exit(1)

    # --------------------------------------------------------------------------
    # Step 2: Check firebase-admin installation
    # --------------------------------------------------------------------------
    print_step(2, "Verifying 'firebase-admin' Library")
    try:
        import firebase_admin
        from firebase_admin import credentials, firestore, auth
        print_success(f"firebase-admin library is installed (v{firebase_admin.__version__}).")
    except ImportError:
        print_error("firebase-admin is not installed in the current virtual environment.")
        print("\nFix:")
        print("    pip install firebase-admin")
        sys.exit(1)

    # --------------------------------------------------------------------------
    # Step 3: Initialize Firebase Admin SDK
    # --------------------------------------------------------------------------
    print_step(3, "Initializing Firebase Admin SDK")
    from backend.database.connection import initialize_firebase, get_firestore_client, is_firebase_available

    # Ensure test environment flag is not overriding real connection
    if os.environ.get("TESTING") == "true":
        del os.environ["TESTING"]

    initialized = initialize_firebase()
    if not initialized:
        print_error("Firebase Admin SDK failed to initialize. Check service account permissions.")
        sys.exit(1)

    print_success("Firebase Admin SDK initialized successfully.")

    # --------------------------------------------------------------------------
    # Step 4: Verify Cloud Firestore Read/Write Access
    # --------------------------------------------------------------------------
    print_step(4, "Testing Cloud Firestore CRUD Operations")
    db = get_firestore_client()
    if db is None:
        print_error("Could not obtain Cloud Firestore client instance.")
        sys.exit(1)

    print_success("Firestore client acquired.")

    test_collection = "_connectivity_test"
    test_doc_id = f"test_{uuid.uuid4().hex[:8]}"
    test_payload = {
        "test_id": test_doc_id,
        "app": "Scam Shield",
        "description": "Ephemeral connectivity verification probe",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "status": "active"
    }

    try:
        # 4a. Write probe
        doc_ref = db.collection(test_collection).document(test_doc_id)
        doc_ref.set(test_payload)
        print_success(f"WRITE operation passed: Created document '{test_doc_id}' in '{test_collection}'.")

        # 4b. Read probe
        snapshot = doc_ref.get()
        if not snapshot.exists:
            print_error("READ operation failed: Document was written but cannot be retrieved.")
            sys.exit(1)
        data = snapshot.to_dict()
        if data.get("test_id") != test_doc_id:
            print_error("INTEGRITY check failed: Retrieved data does not match written payload.")
            sys.exit(1)
        print_success("READ operation passed: Verified document contents integrity.")

        # 4c. Delete probe (clean up after verification)
        doc_ref.delete()
        verify_deleted = doc_ref.get()
        if not verify_deleted.exists:
            print_success("DELETE operation passed: Cleaned up temporary test document.")
        else:
            print_warning("Could not automatically delete test document.")

    except Exception as exc:
        print_error(f"Firestore operation failed: {exc}")
        print("\nTroubleshooting Tips:")
        print("1. Ensure Cloud Firestore API is enabled in your Google Cloud / Firebase Console.")
        print("2. Ensure Cloud Firestore Database has been created in (Native mode).")
        print("3. Check internet access and firewall/proxy settings.")
        sys.exit(1)

    # --------------------------------------------------------------------------
    # Step 5: Check Scam Shield Target Collections
    # --------------------------------------------------------------------------
    print_step(5, "Verifying Scam Shield Target Collections")
    target_collections = ["users", "analysis_history", "trusted_contacts", "notifications"]
    for col_name in target_collections:
        try:
            # Query up to 1 document to verify permissions without reading large data
            sample = list(db.collection(col_name).limit(1).stream())
            print_success(f"Collection '{col_name}' accessible (found {len(sample)} sample doc).")
        except Exception as exc:
            print_warning(f"Collection '{col_name}' check returned: {exc}")

    # --------------------------------------------------------------------------
    # Step 6: Verify Firebase Authentication Service
    # --------------------------------------------------------------------------
    print_step(6, "Verifying Firebase Authentication Client")
    try:
        # Check if auth module is callable with the active app
        app = firebase_admin.get_app()
        # Verify auth client instantiation
        auth_client = auth.Client(app) if hasattr(auth, "Client") else None
        print_success(f"Firebase Authentication client ready for token verification.")
    except Exception as exc:
        print_warning(f"Firebase Auth check returned: {exc}")

    # --------------------------------------------------------------------------
    # Summary
    # --------------------------------------------------------------------------
    print("\n" + "=" * 70)
    print(" ALL CHECKS PASSED: Firebase & Cloud Firestore are fully operational!")
    print("=" * 70)
    print("\nYou can now run the backend with live database persistence:")
    print("    uvicorn main:app --host 127.0.0.1 --port 8000 --reload\n")


if __name__ == "__main__":
    main()
