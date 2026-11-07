"""
Security & Firebase Authentication Verification
Extracts and verifies Firebase ID tokens, enforcing authenticated UID extraction.
Endpoints protected by these dependencies never trust client-provided user IDs.
"""

import logging
from typing import Optional
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials

from backend.config import settings
from backend.models.schemas import UserProfile
from backend.database.connection import get_firebase_app

logger = logging.getLogger("scam_shield.security")

# HTTP Bearer authentication scheme
security_bearer = HTTPBearer(auto_error=False)

# Optional lazy import of firebase_admin.auth
try:
    from firebase_admin import auth as fb_auth
except ImportError:
    fb_auth = None


async def verify_firebase_token(token: str) -> dict:
    """
    Verify a Firebase ID token using the Firebase Admin SDK.
    Returns the decoded token claims dictionary containing 'uid'.
    Raises HTTPException(401) on invalid or expired token.
    """
    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication token is missing.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # Check for local development bypass if explicitly enabled in configuration
    if settings.ALLOW_DEV_AUTH_BYPASS and token.startswith("dev_test_token_"):
        uid = token.replace("dev_test_token_", "dev_user_")
        logger.warning(f"Development auth bypass enabled: Authenticated as mock UID '{uid}'")
        return {
            "uid": uid,
            "email": f"{uid}@example.com",
            "name": "Dev Test User",
            "auth_time": 0,
        }

    # Ensure Firebase app is initialized
    app = get_firebase_app()
    if app is None or fb_auth is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=(
                "Firebase Authentication service is not configured or unavailable on the server. "
                "Please configure valid Firebase service account credentials in .env."
            ),
        )

    try:
        decoded_token = fb_auth.verify_id_token(token, check_revoked=True)
        return decoded_token
    except Exception as exc:
        exc_name = type(exc).__name__
        if "ExpiredIdToken" in exc_name:
            detail_msg = "The provided Firebase ID token has expired. Please refresh your session."
        elif "RevokedIdToken" in exc_name:
            detail_msg = "The provided Firebase ID token has been revoked."
        else:
            detail_msg = "Invalid Firebase ID token. Verification failed."
        logger.warning(f"Failed to verify Firebase ID token: {exc}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=detail_msg,
            headers={"WWW-Authenticate": "Bearer"},
        )


async def get_current_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security_bearer)
) -> UserProfile:
    """
    FastAPI Dependency: Authenticates incoming request using Bearer ID token.
    Enforces that private endpoints can only be accessed with a valid Firebase UID.
    NEVER trusts client-supplied UIDs from query params or request bodies.
    """
    if credentials is None or not credentials.credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication required. Please include 'Authorization: Bearer <token>' header.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    token = credentials.credentials
    claims = await verify_firebase_token(token)

    uid = claims.get("uid")
    if not uid:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token verification succeeded but UID claim is missing.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return UserProfile(
        uid=uid,
        email=claims.get("email"),
        display_name=claims.get("name") or claims.get("display_name"),
        is_anonymous=claims.get("firebase", {}).get("sign_in_provider") == "anonymous",
        auth_time=claims.get("auth_time"),
    )


async def get_optional_current_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security_bearer)
) -> Optional[UserProfile]:
    """
    FastAPI Dependency: Returns authenticated UserProfile if valid Bearer token is provided,
    or None if no authentication headers are present.
    """
    if credentials is None or not credentials.credentials:
        return None
    try:
        return await get_current_user(credentials)
    except HTTPException:
        return None
