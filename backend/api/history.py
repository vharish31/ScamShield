
"""
Analysis History API Endpoints
Provides authenticated access to past scam analysis reports stored in Cloud Firestore.
Strictly isolates user records so users can only access their own history.
"""

import logging
from datetime import datetime
from typing import List

from fastapi import APIRouter, Depends, HTTPException, status

from backend.models.schemas import (
    HistoryListResponse,
    HistoryItemResponse,
    AnalysisResponse,
    UserProfile,
)
from backend.utils.security import get_current_user
from backend.database.connection import get_firestore_client
from backend.services.audio_service import audio_service

logger = logging.getLogger("scam_shield.history")

router = APIRouter(prefix="/api/history", tags=["History & Audit"])


@router.get(
    "",
    response_model=HistoryListResponse,
    summary="List Analysis History",
    description="Retrieves previous analyses belonging to the authenticated user.",
)
async def get_history(current_user: UserProfile = Depends(get_current_user)):
    db = get_firestore_client()

    if db is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cloud Firestore database is currently not available.",
        )

    try:
        query = (
            db.collection("analysis_history")
            .where("user_id", "==", current_user.uid)
        )

        docs = query.stream()
        items: List[HistoryItemResponse] = []

        for doc in docs:
            d = doc.to_dict() or {}

            transcript = d.get("transcript", "")
            preview = transcript[:120] + "..." if len(transcript) > 120 else transcript

            created = d.get("created_at")

            if isinstance(created, str):
                created_dt = datetime.fromisoformat(created)
            elif isinstance(created, datetime):
                created_dt = created
            else:
                created_dt = datetime.utcnow()

            items.append(
                HistoryItemResponse(
                    analysis_id=d.get("analysis_id", doc.id),
                    user_id=d.get("user_id", current_user.uid),
                    risk_score=d.get("risk_score", 0),
                    risk_level=d.get("risk_level", "LOW"),
                    detected_patterns=d.get("detected_patterns", []),
                    transcript_preview=preview,
                    audio_id=d.get("audio_id"),
                    created_at=created_dt,
                )
            )

        items.sort(key=lambda x: x.created_at, reverse=True)

        return HistoryListResponse(
            total=len(items),
            items=items,
        )

    except Exception:
        logger.exception(
            "Failed to fetch history for user '%s'",
            current_user.uid,
        )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Error retrieving analysis history from database.",
        )


@router.get(
    "/{analysis_id}",
    response_model=AnalysisResponse,
    summary="Get Specific Analysis Record",
)
async def get_analysis_detail(
    analysis_id: str,
    current_user: UserProfile = Depends(get_current_user),
):
    db = get_firestore_client()

    if db is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cloud Firestore database is currently not available.",
        )

    doc_ref = db.collection("analysis_history").document(analysis_id)
    snapshot = doc_ref.get()

    if not snapshot.exists:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Analysis report was not found.",
        )

    data = snapshot.to_dict() or {}

    if data.get("user_id") != current_user.uid:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You do not have permission to view this analysis record.",
        )

    created = data.get("created_at")

    if isinstance(created, str):
        created_dt = datetime.fromisoformat(created)
    elif isinstance(created, datetime):
        created_dt = created
    else:
        created_dt = datetime.utcnow()

    return AnalysisResponse(
        analysis_id=data.get("analysis_id", analysis_id),
        transcript=data.get("transcript", ""),
        risk_score=data.get("risk_score", 0),
        risk_level=data.get("risk_level", "LOW"),
        detected_patterns=data.get("detected_patterns", []),
        recommendations=data.get("recommendations", []),
        is_heuristic=data.get("is_heuristic", True),
        confidence_disclaimer=data.get("confidence_disclaimer", ""),
        audio_id=data.get("audio_id"),
        created_at=created_dt,
    )


@router.delete(
    "/{analysis_id}",
    summary="Delete Analysis Record",
    description="Deletes the authenticated user's analysis record and attempts associated audio cleanup.",
)
async def delete_analysis_record(
    analysis_id: str,
    current_user: UserProfile = Depends(get_current_user),
):
    db = get_firestore_client()

    if db is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Cloud Firestore database is currently not available.",
        )

    doc_ref = db.collection("analysis_history").document(analysis_id)
    snapshot = doc_ref.get()

    if not snapshot.exists:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Analysis report was not found.",
        )

    data = snapshot.to_dict() or {}

    if data.get("user_id") != current_user.uid:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You do not have permission to delete this analysis record.",
        )

    # Remove associated audio only if it belongs to this authenticated user.
    audio_id = data.get("audio_id")

    if audio_id:
        try:
            deleted = audio_service.delete_audio(
                audio_id=audio_id,
                user_id=current_user.uid,
            )

            if not deleted:
                logger.warning(
                    "Associated audio '%s' was not found during history cleanup.",
                    audio_id,
                )

        except Exception:
            logger.exception(
                "Audio cleanup failed for analysis '%s'.",
                analysis_id,
            )

    # Delete the history record even if audio cleanup failed.
    doc_ref.delete()

    logger.info(
        "Deleted analysis '%s' for user '%s'.",
        analysis_id,
        current_user.uid,
    )

    return {
        "status": "deleted",
        "analysis_id": analysis_id,
    }
