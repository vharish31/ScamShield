"""
Trusted Contacts API Endpoints
Manages emergency and trusted family contacts stored in Cloud Firestore.
Ensures that all contacts are associated with the verified Firebase UID.
"""

import uuid
import logging
from datetime import datetime, timezone
from typing import List
from fastapi import APIRouter, Depends, HTTPException, status

from backend.models.schemas import ContactCreate, ContactResponse, ContactListResponse, UserProfile
from backend.utils.security import get_current_user
from backend.database.connection import get_firestore_client

logger = logging.getLogger("scam_shield.contacts")

router = APIRouter(prefix="/api/contacts", tags=["Trusted Contacts"])


@router.post(
    "",
    response_model=ContactResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Add Trusted Contact",
    description="Registers a new trusted contact for emergency notifications under the authenticated user's profile.",
)
async def create_contact(
    payload: ContactCreate,
    current_user: UserProfile = Depends(get_current_user),
):
    db = get_firestore_client()
    if db is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cloud Firestore database is currently not available. Check Firebase service account setup.",
        )

    contact_id = str(uuid.uuid4())
    now = datetime.now(timezone.utc)

    contact_data = {
        "id": contact_id,
        "user_id": current_user.uid,
        "name": payload.name.strip(),
        "phone_number": payload.phone_number.strip(),
        "email": str(payload.email).strip() if payload.email else None,
        "relationship": payload.relationship.strip(),
        "notify_on_high_risk": payload.notify_on_high_risk,
        "created_at": now.isoformat(),
    }

    try:
        db.collection("trusted_contacts").document(contact_id).set(contact_data)
        logger.info(f"Added trusted contact '{contact_id}' for user '{current_user.uid}'")
    except Exception as exc:
        logger.error(f"Failed to save contact to Firestore: {exc}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to save contact to database."
        )

    return ContactResponse(
        id=contact_id,
        user_id=current_user.uid,
        name=payload.name,
        phone_number=payload.phone_number,
        email=str(payload.email) if payload.email else None,
        relationship=payload.relationship,
        notify_on_high_risk=payload.notify_on_high_risk,
        created_at=now
    )


@router.get(
    "",
    response_model=ContactListResponse,
    summary="List Trusted Contacts",
    description="Retrieves all registered trusted contacts for the authenticated user.",
)
async def list_contacts(current_user: UserProfile = Depends(get_current_user)):
    db = get_firestore_client()
    if db is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cloud Firestore database is currently not available.",
        )

    try:
        docs = db.collection("trusted_contacts").where("user_id", "==", current_user.uid).stream()
        contacts = []
        for doc in docs:
            d = doc.to_dict()
            contacts.append(
                ContactResponse(
                    id=d["id"],
                    user_id=d["user_id"],
                    name=d["name"],
                    phone_number=d["phone_number"],
                    email=d.get("email"),
                    relationship=d.get("relationship", "Family"),
                    notify_on_high_risk=d.get("notify_on_high_risk", True),
                    created_at=datetime.fromisoformat(d["created_at"]) if isinstance(d["created_at"], str) else d["created_at"]
                )
            )

        return ContactListResponse(total=len(contacts), contacts=contacts)
    except Exception as exc:
        logger.error(f"Failed to list contacts for user '{current_user.uid}': {exc}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Error retrieving contacts from database."
        )


@router.delete(
    "/{contact_id}",
    summary="Delete Trusted Contact",
    description="Removes a trusted contact. The user must be the verified owner of the contact record.",
)
async def delete_contact(
    contact_id: str,
    current_user: UserProfile = Depends(get_current_user),
):
    db = get_firestore_client()
    if db is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cloud Firestore database is currently not available.",
        )

    try:
        doc_ref = db.collection("trusted_contacts").document(contact_id)
        snapshot = doc_ref.get()

        if not snapshot.exists:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Contact with ID '{contact_id}' not found."
            )

        data = snapshot.to_dict()
        if data.get("user_id") != current_user.uid:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You do not have permission to delete this contact record."
            )

        doc_ref.delete()
        logger.info(f"Deleted trusted contact '{contact_id}' by user '{current_user.uid}'")
        return {"status": "deleted", "contact_id": contact_id}
    except HTTPException:
        raise
    except Exception as exc:
        logger.error(f"Failed to delete contact: {exc}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Error deleting contact from database."
        )
