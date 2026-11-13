"""
Scam Shield - Main FastAPI Application
AI-Powered Phone Call Scam Detection System Backend
"""

import sys
from pathlib import Path

# Ensure repository root is in sys.path so 'backend.*' imports resolve from any working directory
_BACKEND_DIR = Path(__file__).resolve().parent
_REPO_ROOT = _BACKEND_DIR.parent
for _p in (str(_REPO_ROOT), str(_BACKEND_DIR)):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException

from backend.config import settings
from backend.models.schemas import HealthResponse, SystemInfoResponse
from backend.database.connection import initialize_firebase, is_firebase_available
from backend.services.transcription_service import transcription_service
from backend.models.ml_interface import ml_adapter

# Import Routers
from backend.api.auth import router as auth_router
from backend.api.audio import router as audio_router
from backend.api.analysis import router as analysis_router
from backend.api.contacts import router as contacts_router
from backend.api.history import router as history_router

# Setup Logging
logging.basicConfig(
    level=logging.INFO if not settings.DEBUG else logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("scam_shield.main")


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Application lifecycle management.
    Performs non-blocking startup checks and graceful resource teardown.
    """
    logger.info(f"Starting {settings.APP_NAME} v{settings.APP_VERSION}...")

    # Attempt Firebase initialization gracefully without crashing startup
    try:
        firebase_ok = initialize_firebase()
        if firebase_ok:
            logger.info("Firebase Cloud Firestore successfully connected.")
        else:
            logger.warning(
                "Firebase not connected. Authentication and Firestore persistence will be inactive "
                "until credentials are placed in the configured path."
            )
    except Exception as exc:
        logger.error(f"Non-fatal Firebase initialization error during startup: {exc}")

    yield

    logger.info("Shutting down Scam Shield backend.")


# Initialize FastAPI application with rich metadata
app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description=settings.APP_DESCRIPTION,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
    lifespan=lifespan,
)

# Configure CORS Middleware for the React frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_origin_regex=r"https://.*\.vercel\.app|https://.*\.onrender\.com|http://localhost:.*|http://127\.0\.0\.1:.*",
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ==============================================================================
# Global Exception Handlers
# ==============================================================================

@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(request: Request, exc: StarletteHTTPException):
    """Handles standard HTTPExceptions with a consistent JSON envelope."""
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "error": {
                "code": f"HTTP_{exc.status_code}",
                "message": exc.detail,
                "details": None,
            }
        },
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """Provides human-readable request schema validation errors."""
    errors = []
    for err in exc.errors():
        loc = " -> ".join(str(l) for l in err.get("loc", []))
        errors.append(f"{loc}: {err.get('msg', 'Invalid value')}")

    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={
            "error": {
                "code": "VALIDATION_ERROR",
                "message": "Input validation failed. Please check the supplied parameters.",
                "details": errors,
            }
        },
    )


@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    """Fallback handler to prevent unhandled stack traces leaking to clients."""
    logger.exception(f"Unhandled exception on {request.method} {request.url.path}: {exc}")
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={
            "error": {
                "code": "INTERNAL_SERVER_ERROR",
                "message": "An unexpected server error occurred while processing the request.",
                "details": str(exc) if settings.DEBUG else None,
            }
        },
    )


# ==============================================================================
# Root & Health Endpoints
# ==============================================================================

@app.get("/", response_model=SystemInfoResponse, tags=["System"])
async def root():
    """Returns basic system metadata and links to API documentation."""
    return SystemInfoResponse(
        app_name=settings.APP_NAME,
        version=settings.APP_VERSION,
        status="running",
        docs_url="/docs",
    )


@app.get("/health", response_model=HealthResponse, tags=["System"])
async def health_check():
    """
    Subsystem Health Check:
    Verifies operational state of API server, Firestore connection,
    Faster-Whisper configuration, and ML model status.
    """
    ml_status = "trained_model_active" if ml_adapter.is_ready() else "rule_based_baseline_active"

    return HealthResponse(
        status="healthy",
        version=settings.APP_VERSION,
        firebase_connected=is_firebase_available(),
        whisper_configured=transcription_service.is_available(),
        ml_model_status=ml_status,
    )


# ==============================================================================
# Register Routers
# ==============================================================================

app.include_router(auth_router)
app.include_router(audio_router)
app.include_router(analysis_router)
app.include_router(contacts_router)
app.include_router(history_router)


if __name__ == "__main__":
    import uvicorn
    # Use "main:app" when executed from inside backend directory
    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
    )

