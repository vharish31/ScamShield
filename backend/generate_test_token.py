"""
Scam Shield - Firebase Authentication Helper & Test Token Generator
Generates test credentials for Swagger UI, Postman, and automated tests.

Usage:
    python generate_test_token.py
"""

import sys
from pathlib import Path

# Add backend directory to path
BACKEND_DIR = Path(__file__).resolve().parent
REPO_ROOT = BACKEND_DIR.parent
for p in (str(REPO_ROOT), str(BACKEND_DIR)):
    if p not in sys.path:
        sys.path.insert(0, p)

import firebase_admin
from firebase_admin import auth
from backend.database.connection import initialize_firebase, get_firebase_app
from backend.config import settings


def main():
    print("=" * 70)
    print(" Scam Shield - Authentication Token Helper")
    print("=" * 70)

    # 1. Dev Bypass Token (Instant Local Testing)
    print("\n[OPTION 1] Instant Development Token (No internet required)")
    print("----------------------------------------------------------------------")
    print("If you set 'ALLOW_DEV_AUTH_BYPASS=True' in backend/.env, you can use:")
    print("    Token: dev_test_token_testuser123")
    print("    Header: Authorization: Bearer dev_test_token_testuser123")
    print("    Resolved UID: dev_user_testuser123")

    # 2. Real Firebase Admin Custom Token
    print("\n[OPTION 2] Live Firebase Custom Token (Generated via Service Account)")
    print("----------------------------------------------------------------------")
    if initialize_firebase():
        test_uid = "test_user_scam_shield"
        try:
            custom_token_bytes = auth.create_custom_token(test_uid)
            custom_token_str = custom_token_bytes.decode("utf-8") if isinstance(custom_token_bytes, bytes) else custom_token_bytes
            print(f"Generated Firebase Custom Token for UID '{test_uid}':\n")
            print(f"{custom_token_str}\n")
            print("Note: Custom tokens are signed by your service account. In client apps")
            print("(React/Flutter), call 'signInWithCustomToken(auth, token)' to receive")
            print("a full Firebase ID Token.")
        except Exception as exc:
            print(f"Could not generate custom token: {exc}")
    else:
        print("Firebase Admin SDK is not initialized. Check your credentials.")

    print("\n[OPTION 3] How Client Frontends (React / Mobile) Authenticate")
    print("----------------------------------------------------------------------")
    print("""
// JavaScript / React Firebase Auth Flow:
import { getAuth, signInWithEmailAndPassword } from "firebase/auth";

const auth = getAuth();
const userCredential = await signInWithEmailAndPassword(auth, email, password);
const idToken = await userCredential.user.getIdToken();

// Send with every API request:
const response = await fetch("http://127.0.0.1:8000/api/analyze/text", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${idToken}`
  },
  body: JSON.stringify({ text: "Sample call transcript" })
});
""")
    print("=" * 70)


if __name__ == "__main__":
    main()

