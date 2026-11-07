"""
Text Preprocessing Service for Transcript Normalization and Feature Extraction
Prepares transcribed text for scam detection and machine learning inference,
while rigorously preserving critical scam indicators, urgency phrases, and monetary cues.
"""

import re
import unicodedata
from typing import Dict, Any, List


class PreprocessingService:
    """Handles text cleaning, canonical normalization, and token preservation."""

    # Common English contractions to expand without altering semantic urgency
    CONTRACTIONS = {
        r"\bcan't\b": "cannot",
        r"\bwon't\b": "will not",
        r"\bdon't\b": "do not",
        r"\bdid't\b": "did not",
        r"\bdidn't\b": "did not",
        r"\bi'm\b": "i am",
        r"\byou're\b": "you are",
        r"\bthey're\b": "they are",
        r"\bit's\b": "it is",
        r"\bthere's\b": "there is",
        r"\bthat's\b": "that is",
        r"\bwhat's\b": "what is",
        r"\bwho's\b": "who is",
        r"\bwe're\b": "we are",
        r"\bhaven't\b": "have not",
        r"\bhasn't\b": "has not",
        r"\bhadn't\b": "had not",
        r"\bwouldn't\b": "would not",
        r"\bshouldn't\b": "should not",
        r"\bcouldn't\b": "could not",
    }

    def normalize_whitespace(self, text: str) -> str:
        """Strip duplicate whitespace, non-standard line breaks, and tabs."""
        if not text:
            return ""
        # Normalize Unicode characters (e.g. smart quotes, full-width numbers)
        text = unicodedata.normalize("NFKC", text)
        # Collapse tabs, newlines, and multiple spaces into a single space
        return re.sub(r"\s+", " ", text).strip()

    def expand_contractions(self, text: str) -> str:
        """Expand standard verbal contractions to facilitate uniform keyword matching."""
        lower = text.lower()
        for pattern, replacement in self.CONTRACTIONS.items():
            lower = re.sub(pattern, replacement, lower)
        return lower

    def clean_text(self, text: str) -> str:
        """
        Produce a clean, human-readable transcript while removing extraneous artifacts.
        Preserves original capitalization, numbers, and basic sentence punctuation.
        """
        if not text:
            return ""
        normalized = self.normalize_whitespace(text)
        # Remove non-printable control characters
        cleaned = "".join(ch for ch in normalized if ch.isprintable())
        return cleaned

    def prepare_for_inference(self, text: str) -> str:
        """
        Standardize text for NLP vectorizers / ML pipelines:
        - Lowercased
        - Contractions expanded
        - Punctuation stripped except currency signs and hyphens
        - Whitespace strictly single-spaced
        """
        if not text:
            return ""
        cleaned = self.clean_text(text)
        expanded = self.expand_contractions(cleaned)
        # Preserve alphanumeric, spaces, and currency symbols ($, Rs, ₹, €, £)
        sanitized = re.sub(r"[^\w\s\$\€\£\₹\-]", " ", expanded)
        return self.normalize_whitespace(sanitized)

    def extract_tokens(self, text: str) -> List[str]:
        """Extract individual tokenized words for pattern analysis."""
        prepared = self.prepare_for_inference(text)
        return [tok for tok in prepared.split() if len(tok) > 1]

    def process(self, raw_text: str) -> Dict[str, Any]:
        """
        Comprehensive preprocessing pipeline returning both presentation-ready
        and ML-ready formats with descriptive metadata.
        """
        cleaned = self.clean_text(raw_text)
        ml_ready = self.prepare_for_inference(cleaned)
        tokens = self.extract_tokens(cleaned)

        return {
            "original_text": raw_text,
            "cleaned_text": cleaned,
            "ml_ready_text": ml_ready,
            "word_count": len(cleaned.split()),
            "token_count": len(tokens),
            "tokens": tokens,
        }


preprocessing_service = PreprocessingService()

