"""
Authentication API Endpoints
Verifies Firebase ID tokens and returns verified user identity profiles.
Enforces that user identity is always cryptographic and never blindly client-supplied.
"""

from fastapi import APIRouter, Depends, HTTPException, status, Header
from typing import Optional

from backend.models.schemas import UserProfile, TokenVerifyRequest
from backend.utils.security import get_current_user, verify_firebase_token

router = APIRouter(prefix="/api/auth", tags=["Authentication"])


@router.post(
    "/verify",
    response_model=UserProfile,
    summary="Verify Firebase ID Token",
    description="Validates a client Firebase ID token cryptographically using Firebase Admin SDK and returns verified UID.",
)
async def verify_token(
    payload: Optional[TokenVerifyRequest] = None,
    authorization: Optional[str] = Header(None, description="Bearer <token>")
):
    token = None
    if authorization and authorization.startswith("Bearer "):
        token = authorization.split("Bearer ", 1)[1].strip()
    elif payload and payload.token:
        token = payload.token.strip()

    if not token:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Token must be provided in either 'Authorization: Bearer <token>' header or request JSON body."
        )

    claims = await verify_firebase_token(token)
    uid = claims.get("uid")
    if not uid:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Verification succeeded but UID claim is missing from token."
        )

    return UserProfile(
        uid=uid,
        email=claims.get("email"),
        display_name=claims.get("name") or claims.get("display_name"),
        is_anonymous=claims.get("firebase", {}).get("sign_in_provider") == "anonymous",
        auth_time=claims.get("auth_time")
    )


@router.get(
    "/me",
    response_model=UserProfile,
    summary="Get Authenticated User Profile",
    description="Protected endpoint returning the profile of the verified Firebase user making the request.",
)
async def get_my_profile(current_user: UserProfile = Depends(get_current_user)):
    return current_user

