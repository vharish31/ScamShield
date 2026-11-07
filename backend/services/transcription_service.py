"""
Speech-to-Text Transcription Service using Faster-Whisper
Provides configurable, cached model loading and robust speech recognition for phone call audio.
Gracefully handles missing packages, silent audio, and transcription errors.
"""

import logging
from pathlib import Path
from typing import Dict, Any, Optional, Union
from backend.config import settings

logger = logging.getLogger("scam_shield.transcription")

# Lazy import of faster_whisper to avoid crashing if binary/dependencies are compiling
_faster_whisper_available = False
try:
    from faster_whisper import WhisperModel
    _faster_whisper_available = True
except ImportError:
    WhisperModel = None
    logger.warning("faster-whisper package is not installed. Live audio transcription will be unavailable.")


class TranscriptionService:
    """
    Manages Whisper model lifecycle and audio transcription.
    Reuses model instance across requests to minimize memory overhead.
    """

    def __init__(self):
        self._model: Optional[Any] = None
        self._model_size = settings.WHISPER_MODEL_SIZE
        self._device = settings.WHISPER_DEVICE
        self._compute_type = settings.WHISPER_COMPUTE_TYPE

    def is_available(self) -> bool:
        """Check whether Faster-Whisper engine is available."""
        return _faster_whisper_available

    def get_model(self) -> Optional[Any]:
        """
        Lazily initialize and return the cached WhisperModel instance.
        Ensures model weights are only loaded into memory when required.
        """
        if not _faster_whisper_available:
            logger.warning("Faster-Whisper is not installed.")
            return None

        if self._model is None:
            logger.info(
                f"Loading Faster-Whisper model '{self._model_size}' "
                f"on {self._device} ({self._compute_type})..."
            )
            try:
                self._model = WhisperModel(
                    self._model_size,
                    device=self._device,
                    compute_type=self._compute_type
                )
                logger.info("Faster-Whisper model loaded successfully.")
            except Exception as exc:
                logger.error(f"Failed to load Faster-Whisper model: {exc}")
                self._model = None
                raise RuntimeError(f"Whisper model initialization failed: {exc}")

        return self._model

    def transcribe(self, audio_path: Union[str, Path]) -> Dict[str, Any]:
        """
        Transcribe audio file into structured text.

        Returns:
            Dict with:
                - transcript: str
                - language: str
                - language_probability: float
                - segments_count: int
        """
        path = Path(audio_path)
        if not path.exists():
            raise FileNotFoundError(f"Audio file not found at: {audio_path}")

        if not self.is_available():
            raise RuntimeError(
                "Speech transcription is unavailable because faster-whisper is not installed. "
                "Please run: pip install faster-whisper"
            )

        model = self.get_model()
        if model is None:
            raise RuntimeError("Faster-Whisper model could not be initialized.")

        try:
            segments, info = model.transcribe(
                str(path),
                beam_size=5,
                vad_filter=True,  # Voice activity detection to ignore silence
                vad_parameters=dict(min_silence_duration_ms=500)
            )

            # Consume segments generator
            transcript_segments = []
            for segment in segments:
                text = segment.text.strip()
                if text:
                    transcript_segments.append(text)

            full_transcript = " ".join(transcript_segments).strip()

            return {
                "transcript": full_transcript,
                "language": info.language if hasattr(info, "language") else "en",
                "language_probability": getattr(info, "language_probability", 1.0),
                "segments_count": len(transcript_segments)
            }

        except Exception as exc:
            logger.error(f"Transcription failed for audio '{path.name}': {exc}")
            raise RuntimeError(f"Transcription error: {exc}")


transcription_service = TranscriptionService()

