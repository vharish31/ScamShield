
"""
Audio Upload and Inspection API Endpoints
Requires Firebase authentication and verifies audio ownership.
"""

from fastapi import APIRouter, UploadFile, File, Depends, HTTPException, status

from backend.models.schemas import AudioUploadResponse, UserProfile
from backend.services.audio_service import audio_service
from backend.utils.security import get_current_user

router = APIRouter(prefix="/api/audio", tags=["Audio Management"])


@router.post(
    "/upload",
    response_model=AudioUploadResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Upload Audio File",
)
async def upload_audio(
    file: UploadFile = File(...),
    current_user: UserProfile = Depends(get_current_user),
):
    """Upload audio and associate it with the authenticated Firebase UID."""
    return await audio_service.save_upload(
        file=file,
        user_id=current_user.uid
    )


@router.get(
    "/{audio_id}",
    summary="Check Audio Status",
)
async def check_audio_status(
    audio_id: str,
    current_user: UserProfile = Depends(get_current_user),
):
    """Check audio status only if the authenticated user owns it."""
    path = audio_service.get_audio_path(
        audio_id=audio_id,
        user_id=current_user.uid
    )

    if not path:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Audio was not found."
        )

    stat = path.stat()

    return {
        "audio_id": audio_id,
        "filename": path.name,
        "format": path.suffix.lstrip("."),
        "file_size_bytes": stat.st_size,
        "status": "ready_for_analysis"
    }


@router.delete(
    "/{audio_id}",
    summary="Delete Audio File",
)
async def delete_audio(
    audio_id: str,
    current_user: UserProfile = Depends(get_current_user),
):
    """Delete audio only if the authenticated user owns it."""
    success = audio_service.delete_audio(
        audio_id=audio_id,
        user_id=current_user.uid
    )

    if not success:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Audio was not found."
        )

    return {
        "status": "deleted",
        "audio_id": audio_id
    }
