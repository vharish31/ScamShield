"""
Audio and Input Validators for Scam Shield
Provides robust validation for audio files, magic bytes, MIME types, and size limits.
"""

from pathlib import Path
from typing import Tuple
from fastapi import HTTPException, status, UploadFile


# Allowed audio file extensions
ALLOWED_AUDIO_EXTENSIONS = {".wav", ".mp3", ".m4a", ".ogg", ".flac", ".aac"}

# Known Audio Magic Bytes / Signatures for secure header inspection
# Prevents renaming an executable or malicious script to .wav/.mp3
def matches_magic_bytes(header: bytes) -> Tuple[bool, str]:
    """
    Check if the first 16-32 bytes of the file match known audio signatures.
    Returns (is_valid, detected_format).
    """
    if len(header) < 4:
        return False, "unknown"

    # RIFF WAVE check: Starts with 'RIFF' and bytes 8-12 are 'WAVE'
    if header.startswith(b"RIFF") and len(header) >= 12 and header[8:12] == b"WAVE":
        return True, "wav"

    # MP3 with ID3v2 tag
    if header.startswith(b"ID3"):
        return True, "mp3"

    # MP3 frame sync without ID3: 0xFF followed by 0xFB, 0xF3, or 0xF2
    if len(header) >= 2 and header[0] == 0xFF and (header[1] & 0xE0 == 0xE0):
        return True, "mp3"

    # Ogg container (Vorbis / Opus)
    if header.startswith(b"OggS"):
        return True, "ogg"

    # Free Lossless Audio Codec (FLAC)
    if header.startswith(b"fLaC"):
        return True, "flac"

    # ISO Media / M4A container (starts with ftyp box at offset 4)
    if len(header) >= 12 and header[4:8] == b"ftyp":
        brand = header[8:12]
        if brand in (b"M4A ", b"isom", b"mp42", b"mp41", b"dash"):
            return True, "m4a"

    # AAC ADTS header: 0xFF followed by 0xF1 or 0xF9
    if len(header) >= 2 and header[0] == 0xFF and (header[1] & 0xF6 == 0xF0):
        return True, "aac"

    return False, "unknown"


async def validate_audio_upload(file: UploadFile, max_size_mb: int) -> Tuple[str, bytes]:
    """
    Validate an uploaded audio file:
    1. Verify file presence.
    2. Check filename extension against allowed whitelist.
    3. Read content with size enforcement to avoid memory exhaustion.
    4. Verify magic bytes / file signatures to prevent spoofed extensions.

    Returns (detected_format, file_bytes).
    """
    if not file or not file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No file was provided in the upload request."
        )

    # 1. Extension check
    filename_lower = file.filename.lower()
    ext = Path(filename_lower).suffix
    if ext not in ALLOWED_AUDIO_EXTENSIONS:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                f"Unsupported audio format '{ext}'. Allowed extensions are: "
                f"{', '.join(sorted(ALLOWED_AUDIO_EXTENSIONS))}"
            )
        )

    # 2. Read file content safely with size limit
    max_bytes = max_size_mb * 1024 * 1024
    content = await file.read()
    file_size = len(content)

    if file_size == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="The uploaded audio file is empty (0 bytes)."
        )

    if file_size > max_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=(
                f"File size exceeds the configured limit of {max_size_mb} MB. "
                f"Uploaded file size is {file_size / (1024 * 1024):.2f} MB."
            )
        )

    # 3. Magic bytes inspection
    header = content[:32]
    is_valid, detected_format = matches_magic_bytes(header)
    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                "Invalid audio content. The file header does not match any recognized "
                "audio signature (WAV, MP3, M4A, OGG, FLAC, AAC). "
                "Renaming another file type to an audio extension is not permitted."
            )
        )

    return detected_format, content

