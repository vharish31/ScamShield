"""
Machine Learning Model Integration Interface
Provides an extensible abstract adapter for teammate's trained ML classification models.
Strictly avoids assuming fixed file formats, vectorizers, or class labels until provided.
"""

import os
import logging
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Optional, Dict, Any, Tuple

from backend.config import settings

logger = logging.getLogger("scam_shield.ml_interface")


class BaseScamModel(ABC):
    """Abstract base class defining the required contract for scam classification models."""

    @abstractmethod
    def load(self, model_path: Path, vectorizer_path: Optional[Path] = None) -> bool:
        """Load model weights and optional vectorizer from disk."""
        pass

    @abstractmethod
    def predict(self, text: str) -> Dict[str, Any]:
        """
        Execute inference on preprocessed text.
        Must return a dict containing at least:
            - is_scam: bool
            - scam_probability: float (0.0 to 1.0)
            - label: str
            - confidence: float
        """
        pass

    @abstractmethod
    def is_ready(self) -> bool:
        """Check if model is currently loaded and ready for inference."""
        pass


class ConfigurableMLAdapter(BaseScamModel):
    """
    Adapter for scikit-learn or similar serialized pipelines (e.g. TF-IDF + LogisticRegression / XGBoost).
    Configurable via environment variables without hardcoding assumptions.
    """

    def __init__(self):
        self._model = None
        self._vectorizer = None
        self._is_loaded = False

    def load(self, model_path: Path, vectorizer_path: Optional[Path] = None) -> bool:
        """Attempt to load trained artifacts using joblib or pickle."""
        if not model_path.exists():
            logger.info(f"ML model file not found at '{model_path}'. Running in rule-based baseline mode.")
            self._is_loaded = False
            return False

        try:
            import joblib

            self._model = joblib.load(str(model_path))
            logger.info(f"Loaded ML model from {model_path}")

            if vectorizer_path and vectorizer_path.exists():
                self._vectorizer = joblib.load(str(vectorizer_path))
                logger.info(f"Loaded vectorizer from {vectorizer_path}")

            self._is_loaded = True
            return True
        except Exception as exc:
            logger.error(f"Failed to load ML artifacts: {exc}")
            self._is_loaded = False
            return False

    def is_ready(self) -> bool:
        return self._is_loaded and self._model is not None

    def predict(self, text: str) -> Dict[str, Any]:
        """
        Perform prediction using loaded model and vectorizer.
        Dynamically handles whether the pipeline already includes vectorization.
        """
        if not self.is_ready():
            return {
                "is_scam": False,
                "scam_probability": 0.0,
                "label": "unclassified",
                "confidence": 0.0,
                "model_available": False,
            }

        try:
            # Transform text if a separate vectorizer exists
            features = [text]
            if self._vectorizer is not None:
                features = self._vectorizer.transform(features)

            # Check for probability estimates
            if hasattr(self._model, "predict_proba"):
                probs = self._model.predict_proba(features)[0]
                # Assume binary classification where class 1 is scam
                scam_prob = float(probs[1]) if len(probs) > 1 else float(probs[0])
            elif hasattr(self._model, "decision_function"):
                decision = self._model.decision_function(features)[0]
                # Simple sigmoid approximation
                import math
                scam_prob = 1.0 / (1.0 + math.exp(-decision))
            else:
                pred = self._model.predict(features)[0]
                scam_prob = 1.0 if pred in (1, "scam", "fraud") else 0.0

            threshold = settings.ML_DECISION_THRESHOLD
            is_scam = scam_prob >= threshold

            return {
                "is_scam": is_scam,
                "scam_probability": scam_prob,
                "label": "SCAM" if is_scam else "LEGITIMATE",
                "confidence": scam_prob if is_scam else (1.0 - scam_prob),
                "model_available": True,
            }
        except Exception as exc:
            logger.error(f"Error during ML model inference: {exc}")
            return {
                "is_scam": False,
                "scam_probability": 0.0,
                "label": "error",
                "confidence": 0.0,
                "model_available": False,
                "error": str(exc),
            }


# Singleton instance of the ML adapter
ml_adapter = ConfigurableMLAdapter()

