
"""
Analysis API Endpoints
Coordinates end-to-end scam analysis with authenticated audio ownership.
"""

import uuid
import logging
from datetime import datetime, timezone
from typing import Optional

from fastapi import (
    APIRouter,
    UploadFile,
    File,
    Form,
    Depends,
    HTTPException,
    status,
)

from backend.models.schemas import (
    AnalysisResponse,
    AnalyzeTextRequest,
    UserProfile,
)
from backend.services.audio_service import audio_service
from backend.services.transcription_service import transcription_service
from backend.services.scam_detection_service import scam_detection_service
from backend.services.risk_scoring_service import risk_scoring_service
from backend.services.notification_service import notification_service
from backend.utils.security import get_current_user, get_optional_current_user
from backend.database.connection import get_firestore_client

logger = logging.getLogger("scam_shield.analysis")

router = APIRouter(prefix="/api/analyze", tags=["Analysis & Detection"])


@router.post(
    "/audio",
    response_model=AnalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="Analyze Phone Call Audio",
)
async def analyze_audio(
    audio_id: Optional[str] = Form(None),
    file: Optional[UploadFile] = File(None),
    current_user: UserProfile = Depends(get_current_user),
):
    """Analyze audio only when it belongs to the authenticated user."""

    if file is not None and file.filename:
        upload_resp = await audio_service.save_upload(
            file=file,
            user_id=current_user.uid,
        )
        active_audio_id = upload_resp.audio_id
    else:
        active_audio_id = audio_id

    if not active_audio_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Provide an audio_id or an audio file.",
        )

    # Verify ownership before accessing the local audio file.
    audio_path = audio_service.get_audio_path(
        audio_id=active_audio_id,
        user_id=current_user.uid,
    )

    if not audio_path:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Audio was not found.",
        )

    if not transcription_service.is_available():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Speech-to-text service is unavailable.",
        )

    try:
        transcription_result = transcription_service.transcribe(audio_path)
        transcript = transcription_result.get("transcript", "").strip()

    except Exception:
        logger.exception("Audio transcription failed for audio ID %s", active_audio_id)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Audio transcription failed. Please try again.",
        )

    if not transcript:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="The audio recording contains no detectable speech or is silent.",
        )

    detection_result = scam_detection_service.detect(transcript)
    evaluation = risk_scoring_service.evaluate(detection_result)

    analysis_id = str(uuid.uuid4())
    user_id = current_user.uid

    response_payload = AnalysisResponse(
        analysis_id=analysis_id,
        transcript=transcript,
        risk_score=evaluation["risk_score"],
        risk_level=evaluation["risk_level"],
        detected_patterns=evaluation["detected_patterns"],
        recommendations=evaluation["recommendations"],
        is_heuristic=evaluation["is_heuristic"],
        confidence_disclaimer=evaluation["confidence_disclaimer"],
        audio_id=active_audio_id,
        created_at=datetime.now(timezone.utc),
    )

    db = get_firestore_client()

    if db is not None:
        try:
            record_data = response_payload.model_dump(mode="json")
            record_data["user_id"] = user_id

            db.collection("analysis_history").document(analysis_id).set(record_data)

        except Exception:
            logger.exception("Could not persist audio analysis record.")

    # Notifications are not sent until explicit user consent is implemented.
    # Having trusted contacts configured does not itself constitute consent.

    return response_payload


@router.post(
    "/text",
    response_model=AnalysisResponse,
    status_code=status.HTTP_200_OK,
    summary="Analyze Call Transcript Text Directly",
)
async def analyze_text(
    payload: AnalyzeTextRequest,
    current_user: Optional[UserProfile] = Depends(get_optional_current_user),
):
    """Analyze text transcripts; anonymous access remains available."""

    raw_text = payload.text.strip()

    if not raw_text:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Input text cannot be empty.",
        )

    detection_result = scam_detection_service.detect(raw_text)
    evaluation = risk_scoring_service.evaluate(detection_result)

    analysis_id = str(uuid.uuid4())
    user_id = current_user.uid if current_user else "anonymous"

    response_payload = AnalysisResponse(
        analysis_id=analysis_id,
        transcript=detection_result["transcript"],
        risk_score=evaluation["risk_score"],
        risk_level=evaluation["risk_level"],
        detected_patterns=evaluation["detected_patterns"],
        recommendations=evaluation["recommendations"],
        is_heuristic=evaluation["is_heuristic"],
        confidence_disclaimer=evaluation["confidence_disclaimer"],
        audio_id=None,
        created_at=datetime.now(timezone.utc),
    )

    db = get_firestore_client()

    if db is not None:
        try:
            record_data = response_payload.model_dump(mode="json")
            record_data["user_id"] = user_id

            db.collection("analysis_history").document(analysis_id).set(record_data)

        except Exception:
            logger.exception("Could not persist text analysis record.")

    return response_payload
