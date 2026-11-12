"""
Scam Shield - Application Configuration
Loads environment variables safely and provides structured, typed settings.
"""

import os
from pathlib import Path
from typing import List
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field

# Base directory for the backend (ScamShield/backend)
BASE_DIR = Path(__file__).resolve().parent


class Settings(BaseSettings):
    """
    Application configuration settings loaded from environment or .env file.
    """
    # Application Info
    APP_NAME: str = "Scam Shield API"
    APP_VERSION: str = "1.0.0"
    APP_DESCRIPTION: str = "AI-Powered Phone Call Scam Detection System Backend"
    DEBUG: bool = True
    HOST: str = "127.0.0.1"
    PORT: int = 8000

    # CORS Configuration
    ALLOWED_ORIGINS: str = (
        "http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173"
    )

    # Firebase Configuration
    FIREBASE_SERVICE_ACCOUNT_PATH: str = "./firebase-service-account.json"

    # Audio Processing & Storage
    MAX_AUDIO_SIZE_MB: int = Field(default=25, description="Maximum audio file size in MB")
    UPLOAD_DIRECTORY: str = Field(default="uploads", description="Directory to store uploaded audio")

    # Speech-to-Text (Faster-Whisper)
    WHISPER_MODEL_SIZE: str = Field(default="base", description="Whisper model: tiny, base, small, medium, large-v3")
    WHISPER_DEVICE: str = "cpu"
    WHISPER_COMPUTE_TYPE: str = "int8"

    # Machine Learning Model Integration
    ML_MODEL_ENABLED: bool = True
    ML_MODEL_PATH: str = "models/artifacts/scam_classifier.joblib"
    ML_VECTORIZER_PATH: str = "models/artifacts/vectorizer.joblib"
    ML_DECISION_THRESHOLD: float = 0.5

    # Notification Service
    NOTIFICATION_ENABLED: bool = False
    NOTIFICATION_DRY_RUN: bool = True

    # Security & Development
    ALLOW_DEV_AUTH_BYPASS: bool = False

    model_config = SettingsConfigDict(
        env_file=str(BASE_DIR / ".env"),
        env_file_encoding="utf-8",
        extra="ignore"
    )

    @property
    def cors_origins(self) -> List[str]:
        """Convert comma-separated origins string to a Python list."""
        if isinstance(self.ALLOWED_ORIGINS, list):
            return self.ALLOWED_ORIGINS
        return [origin.strip() for origin in self.ALLOWED_ORIGINS.split(",") if origin.strip()]

    @property
    def upload_path(self) -> Path:
        """Resolve upload directory as an absolute Path."""
        p = Path(self.UPLOAD_DIRECTORY)
        if not p.is_absolute():
            p = BASE_DIR / p
        p.mkdir(parents=True, exist_ok=True)
        return p

    @property
    def firebase_credential_path(self) -> Path:
        """Resolve Firebase credential path as an absolute Path with fallback check."""
        p = Path(self.FIREBASE_SERVICE_ACCOUNT_PATH)
        if not p.is_absolute():
            p = BASE_DIR / p

        # Graceful check for Windows duplicate extension (e.g. .json.json)
        if not p.exists():
            double_ext = Path(str(p) + ".json")
            if double_ext.exists():
                return double_ext
        return p


settings = Settings()
