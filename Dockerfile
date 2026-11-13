# ==============================================================================
# ScamShield Backend Production Dockerfile
# Optimized for Render, Railway, Fly.io, and Google Cloud Run
# ==============================================================================

FROM python:3.11-slim

# Avoid buffering and bytecode generation
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PORT=8000

WORKDIR /app

# Install system dependencies (ffmpeg for audio transcription)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies first for optimal Docker layer caching
COPY backend/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# Copy application backend code (excluding dataset and venv via .dockerignore)
COPY backend /app/backend

# Create uploads and model artifacts directory if not present
RUN mkdir -p /app/backend/uploads /app/backend/models/artifacts

EXPOSE 8000

# Run uvicorn server binding to 0.0.0.0 and dynamic PORT
CMD ["sh", "-c", "uvicorn backend.main:app --host 0.0.0.0 --port ${PORT:-8000}"]

