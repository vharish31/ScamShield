"""
Pydantic Schemas for Scam Shield
Defines structured request and response formats with validation rules.
"""

from typing import List, Optional, Any
import re
from datetime import datetime, timezone
from pydantic import BaseModel, Field, field_validator


# ==============================================================================
# Health & Status Schemas
# ==============================================================================

class HealthResponse(BaseModel):
    status: str = Field(default="healthy", description="Application operational status")
    version: str = Field(description="API version")
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    firebase_connected: bool = Field(description="Firestore connection status")
    whisper_configured: bool = Field(description="Whether speech-to-text is available")
    ml_model_status: str = Field(description="ML model integration state (baseline or trained)")


class SystemInfoResponse(BaseModel):
    app_name: str
    version: str
    status: str
    docs_url: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


# ==============================================================================
# Auth Schemas
# ==============================================================================

class UserProfile(BaseModel):
    uid: str = Field(..., description="Verified Firebase UID")
    email: Optional[str] = Field(None, description="User email address")
    display_name: Optional[str] = Field(None, description="Display name if available")
    is_anonymous: bool = Field(default=False)
    auth_time: Optional[int] = None


class TokenVerifyRequest(BaseModel):
    token: Optional[str] = Field(None, description="Firebase ID token string")


# ==============================================================================
# Audio Upload Schemas
# ==============================================================================

class AudioUploadResponse(BaseModel):
    audio_id: str = Field(..., description="Unique UUID assigned to uploaded audio")
    filename: str = Field(..., description="Internal stored filename")
    original_filename: str = Field(..., description="Original user file name")
    file_size_bytes: int = Field(..., description="File size in bytes")
    format: str = Field(..., description="Detected audio format extension")
    status: str = Field(default="uploaded", description="Upload lifecycle status")
    upload_time: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


# ==============================================================================
# Analysis & Scam Detection Schemas
# ==============================================================================

class AnalyzeAudioRequest(BaseModel):
    audio_id: Optional[str] = Field(None, description="ID of previously uploaded audio")


class AnalyzeTextRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=10000, description="Raw call transcript text to analyze")


class AnalysisResponse(BaseModel):
    """
    Standard analysis output strictly adhering to prompt section 6 format.
    """
    analysis_id: str = Field(..., description="Unique analysis record identifier")
    transcript: str = Field(..., description="Transcribed or processed audio text")
    risk_score: int = Field(..., ge=0, le=100, description="Heuristic risk score from 0 to 100")
    risk_level: str = Field(..., description="LOW, MEDIUM, HIGH, or CRITICAL")
    detected_patterns: List[str] = Field(
        default_factory=list,
        description="Identified psychological and financial manipulation patterns"
    )
    recommendations: List[str] = Field(
        default_factory=list,
        description="Actionable safety guidance for the user"
    )
    is_heuristic: bool = Field(
        default=True,
        description="Indicates that score is an expert heuristic assessment rather than definitive proof"
    )
    confidence_disclaimer: str = Field(
        default="This risk score is a heuristic safety indicator and does not prove fraud. Always independently verify suspicious callers.",
        description="Legal and analytical disclaimer"
    )
    audio_id: Optional[str] = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


# ==============================================================================
# History Schemas
# ==============================================================================

class HistoryItemResponse(BaseModel):
    analysis_id: str
    user_id: str
    risk_score: int
    risk_level: str
    detected_patterns: List[str]
    transcript_preview: str
    audio_id: Optional[str] = None
    created_at: datetime


class HistoryListResponse(BaseModel):
    total: int
    items: List[HistoryItemResponse]


# ==============================================================================
# Trusted Contacts Schemas
# ==============================================================================

class ContactCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=100, description="Contact full name")
    phone_number: str = Field(..., min_length=5, max_length=20, description="E.164 or formatted phone number")
    email: Optional[str] = Field(None, description="Optional contact email for alerts")
    relationship: str = Field(default="Family", description="e.g. Spouse, Parent, Child, Trusted Friend")
    notify_on_high_risk: bool = Field(
        default=True,
        description="Whether to alert this contact if a HIGH or CRITICAL call is detected (requires user consent)"
    )

    @field_validator("email")
    @classmethod
    def validate_email(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        v = v.strip()
        if not v:
            return None
        if not re.match(r"^[^@\s]+@[^@\s]+\.[^@\s]+$", v):
            raise ValueError("Invalid email address format.")
        return v

    @field_validator("phone_number")
    @classmethod
    def clean_phone(cls, v: str) -> str:
        cleaned = "".join(ch for ch in v if ch.isdigit() or ch in "+-() ")
        if len(cleaned.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")) < 5:
            raise ValueError("Phone number must have at least 5 digits.")
        return v.strip()


class ContactResponse(BaseModel):
    id: str
    user_id: str
    name: str
    phone_number: str
    email: Optional[str] = None
    relationship: str
    notify_on_high_risk: bool
    created_at: datetime


class ContactListResponse(BaseModel):
    total: int
    contacts: List[ContactResponse]


# ==============================================================================
# Notification Schemas
# ==============================================================================

class NotificationLog(BaseModel):
    id: str
    user_id: str
    analysis_id: str
    recipient: str
    channel: str  # email, fcm, sms, mock
    status: str   # sent, dry_run, failed, skipped
    message: str
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


# ==============================================================================
# Standard Error Response
# ==============================================================================

class ErrorDetail(BaseModel):
    code: str
    message: str
    details: Optional[Any] = None


class StandardErrorResponse(BaseModel):
    error: ErrorDetail
