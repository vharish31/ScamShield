
"""
Audio Storage and Lifecycle Management Service
Stores audio files locally and tracks ownership in Cloud Firestore.
"""

import logging
import uuid
from pathlib import Path
from typing import Optional

from fastapi import UploadFile, HTTPException, status

from backend.config import settings
from backend.database.connection import get_firestore_client
from backend.utils.validators import validate_audio_upload
from backend.models.schemas import AudioUploadResponse

logger = logging.getLogger("scam_shield.audio_service")


class AudioService:
    """Manages audio storage, ownership, retrieval, and deletion."""

    def __init__(self, upload_dir: Optional[Path] = None):
        self.upload_dir = upload_dir or settings.upload_path
        self.upload_dir.mkdir(parents=True, exist_ok=True)

    @staticmethod
    def _validate_audio_id(audio_id: str) -> str:
        """Accept only valid UUID audio identifiers."""
        try:
            return str(uuid.UUID(audio_id))
        except (ValueError, TypeError, AttributeError):
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Audio file was not found."
            )

    def _get_database(self):
        """Return Firestore client or fail safely."""
        db = get_firestore_client()

        if db is None:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Audio storage metadata service is unavailable."
            )

        return db

    async def save_upload(
        self,
        file: UploadFile,
        user_id: str
    ) -> AudioUploadResponse:
        """
        Validate and save audio locally.
        Store metadata and owner UID in Firestore.
        """
        if not user_id:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Authenticated user is required."
            )

        detected_format, content = await validate_audio_upload(
            file=file,
            max_size_mb=settings.MAX_AUDIO_SIZE_MB
        )

        audio_id = str(uuid.uuid4())
        safe_filename = f"{audio_id}.{detected_format}"
        file_path = self.upload_dir / safe_filename

        db = self._get_database()

        response = AudioUploadResponse(
            audio_id=audio_id,
            filename=safe_filename,
            original_filename=file.filename or "unknown_audio",
            file_size_bytes=len(content),
            format=detected_format,
            status="uploaded"
        )

        try:
            file_path.write_bytes(content)

            metadata = {
                "audio_id": audio_id,
                "user_id": user_id,
                "filename": safe_filename,
                "original_filename": response.original_filename,
                "file_size_bytes": len(content),
                "format": detected_format,
                "upload_time": response.upload_time,
                "status": "uploaded"
            }

            db.collection("audio_uploads").document(audio_id).set(metadata)

            logger.info(
                "Audio upload saved: %s for authenticated user",
                audio_id
            )

            return response

        except HTTPException:
            if file_path.exists():
                file_path.unlink()
            raise

        except Exception as exc:
            if file_path.exists():
                file_path.unlink()

            logger.error("Audio upload persistence failed: %s", exc)

            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Could not save audio upload metadata."
            )

    def get_audio_path(
        self,
        audio_id: str,
        user_id: str
    ) -> Optional[Path]:
        """Return audio path only when the requesting user owns it."""
        if not user_id:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Authenticated user is required."
            )

        audio_id = self._validate_audio_id(audio_id)
        db = self._get_database()

        try:
            document = (
                db.collection("audio_uploads")
                .document(audio_id)
                .get()
            )

            if not document.exists:
                return None

            metadata = document.to_dict() or {}

            if metadata.get("user_id") != user_id:
                return None

            filename = metadata.get("filename")

            if not filename or Path(filename).name != filename:
                return None

            path = self.upload_dir / filename

            if not path.is_file():
                return None

            return path

        except HTTPException:
            raise

        except Exception as exc:
            logger.error("Audio ownership lookup failed: %s", exc)

            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Could not verify audio ownership."
            )

    def delete_audio(
        self,
        audio_id: str,
        user_id: str
    ) -> bool:
        """Delete audio only after verifying its Firestore owner."""
        path = self.get_audio_path(audio_id, user_id)

        if path is None:
            return False

        db = self._get_database()
        audio_id = self._validate_audio_id(audio_id)

        try:
            path.unlink()

            db.collection("audio_uploads").document(audio_id).delete()

            logger.info("Deleted audio upload: %s", audio_id)
            return True

        except HTTPException:
            raise

        except Exception as exc:
            logger.error("Audio deletion failed: %s", exc)

            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Could not complete audio deletion."
            )


audio_service = AudioService()
