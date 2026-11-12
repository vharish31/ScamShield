"""
ScamShield Machine Learning Model Training Pipeline
Trains a high-performance multilingual scam classification model using the dataset in backend/dataset.
Follows ML Best Practices:
  - Stratified 80/20 train/test split strictly before featurization
  - Multilingual TF-IDF feature extraction
  - Comparison across multiple classifiers (ComplementNB, LogisticRegression, Calibrated LinearSVC)
  - Full evaluation metrics: Accuracy, Precision, Recall, F1-Score, ROC-AUC, and Confusion Matrix
  - Serializes artifacts to backend/models/artifacts/ for immediate production inference
"""

import os
import sys
import time
import re
from pathlib import Path
from typing import List, Tuple

# Enable unbuffered / line-buffered stdout for real-time streaming
try:
    sys.stdout.reconfigure(line_buffering=True)
except Exception:
    pass

import joblib
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression, SGDClassifier
from sklearn.naive_bayes import ComplementNB
from sklearn.calibration import CalibratedClassifierCV
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    confusion_matrix,
    classification_report
)

# Setup directories
REPO_ROOT = Path("D:/ScamShield")
DATASET_DIR = REPO_ROOT / "backend" / "dataset" / "Text_samples"
ARTIFACTS_DIR = REPO_ROOT / "backend" / "models" / "artifacts"
ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)

MODEL_OUTPUT_PATH = ARTIFACTS_DIR / "scam_classifier.joblib"
VECTORIZER_OUTPUT_PATH = ARTIFACTS_DIR / "vectorizer.joblib"

# Clean speaker headers and punctuation
SPEAKER_PREFIX_REGEX = re.compile(r'^(Speaker\s*\d+|User|Caller|Agent|Victim|Scammer)\s*[:：\-]\s*', re.IGNORECASE)

def clean_dialogue_text(text: str) -> str:
    cleaned_lines = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        line = SPEAKER_PREFIX_REGEX.sub('', line).strip()
        if line:
            cleaned_lines.append(line)
    return " ".join(cleaned_lines)

def load_data(
    dataset_dir: Path,
    samples_per_language: int = 4000
) -> Tuple[List[str], List[int], List[str]]:
    """
    Load balanced samples across all language directories.
    Returns:
        texts: list of preprocessed dialogues
        labels: list of binary labels (0 = legit, 1 = scam)
        languages: list of language names for tracking
    """
    texts = []
    labels = []
    languages = []

    print(f"Scanning dataset from: {dataset_dir}")
    t0 = time.time()

    lang_dirs = sorted([d for d in dataset_dir.iterdir() if d.is_dir()])
    
    for lang_dir in lang_dirs:
        lang_name = lang_dir.name
        legit_dir = lang_dir / "legit"
        scam_dir = lang_dir / "scam"

        # Load legit files
        legit_loaded = 0
        if legit_dir.is_dir():
            with os.scandir(legit_dir) as entries:
                for entry in entries:
                    if entry.is_file() and entry.name.endswith(".txt"):
                        try:
                            with open(entry.path, "r", encoding="utf-8", errors="replace") as f:
                                raw = f.read()
                            cleaned = clean_dialogue_text(raw)
                            if len(cleaned) >= 20:
                                texts.append(cleaned)
                                labels.append(0)
                                languages.append(lang_name)
                                legit_loaded += 1
                                if legit_loaded >= samples_per_language // 2:
                                    break
                        except Exception:
                            continue

        # Load scam files
        scam_loaded = 0
        if scam_dir.is_dir():
            with os.scandir(scam_dir) as entries:
                for entry in entries:
                    if entry.is_file() and entry.name.endswith(".txt"):
                        try:
                            with open(entry.path, "r", encoding="utf-8", errors="replace") as f:
                                raw = f.read()
                            cleaned = clean_dialogue_text(raw)
                            if len(cleaned) >= 20:
                                texts.append(cleaned)
                                labels.append(1)
                                languages.append(lang_name)
                                scam_loaded += 1
                                if scam_loaded >= samples_per_language // 2:
                                    break
                        except Exception:
                            continue

        print(f"  [{lang_name:16s}] Loaded {legit_loaded:5d} legit, {scam_loaded:5d} scam (Total: {legit_loaded + scam_loaded:5d})")

    elapsed = time.time() - t0
    print(f"Successfully loaded {len(texts):,d} samples across {len(lang_dirs)} languages in {elapsed:.2f}s")
    return texts, labels, languages


def train_and_evaluate():
    print("=" * 70)
    print(" ScamShield ML Training Pipeline - Scam Detection Classification ")
    print("=" * 70)

    # 1. Load dataset (e.g. 4,000 per language across 10 languages = 40,000 diverse samples)
    texts, labels, languages = load_data(DATASET_DIR, samples_per_language=4000)
    y = np.array(labels)

    scam_count = np.sum(y == 1)
    legit_count = np.sum(y == 0)
    print(f"\nClass balance: Legit={legit_count:,d} ({legit_count/len(y)*100:.1f}%), Scam={scam_count:,d} ({scam_count/len(y)*100:.1f}%)")

    # 2. Strict ML Featurization Ordering: Train/Test Split BEFORE vectorizer fitting
    print("\nSplitting data into 80% Train, 20% Holdout Test (stratified)...")
    X_train_raw, X_test_raw, y_train, y_test = train_test_split(
        texts, y, test_size=0.20, random_state=42, stratify=y
    )
    print(f"Training samples:   {len(X_train_raw):,d}")
    print(f"Holdout test samples: {len(X_test_raw):,d}")

    # 3. Multilingual TF-IDF Feature Extraction
    print("\nFitting Multilingual TF-IDF Vectorizer on training data...")
    t0 = time.time()
    vectorizer = TfidfVectorizer(
        ngram_range=(1, 2),
        max_features=35000,
        sublinear_tf=True,
        min_df=2,
        strip_accents="unicode"
    )
    X_train = vectorizer.fit_transform(X_train_raw)
    X_test = vectorizer.transform(X_test_raw)
    print(f"Vocabulary size: {len(vectorizer.vocabulary_):,d} features (fit in {time.time() - t0:.2f}s)")

    # 4. Model Training & Comparison
    models = {
        "Complement Naive Bayes": ComplementNB(alpha=0.1),
        "Logistic Regression (Balanced)": LogisticRegression(
            C=2.0, max_iter=1000, class_weight="balanced", random_state=42, solver="liblinear"
        ),
        "Calibrated SGD Classifier": CalibratedClassifierCV(
            estimator=SGDClassifier(loss="log_loss", penalty="l2", alpha=1e-4, random_state=42, class_weight="balanced"),
            method="sigmoid",
            cv=3
        )
    }

    results = {}
    best_f1 = -1.0
    champion_name = None
    champion_model = None

    print("\n" + "=" * 70)
    print(" Model Comparison & Evaluation on Test Set")
    print("=" * 70)

    for name, model in models.items():
        print(f"\nTraining [{name}]...")
        t_start = time.time()
        model.fit(X_train, y_train)
        train_time = time.time() - t_start

        # Inference on test set
        y_pred = model.predict(X_test)
        if hasattr(model, "predict_proba"):
            y_proba = model.predict_proba(X_test)[:, 1]
        elif hasattr(model, "decision_function"):
            df = model.decision_function(X_test)
            y_proba = 1.0 / (1.0 + np.exp(-df))
        else:
            y_proba = y_pred.astype(float)

        acc = accuracy_score(y_test, y_pred)
        prec = precision_score(y_test, y_pred, zero_division=0)
        rec = recall_score(y_test, y_pred, zero_division=0)
        f1 = f1_score(y_test, y_pred, zero_division=0)
        auc = roc_auc_score(y_test, y_proba)
        cm = confusion_matrix(y_test, y_pred)

        results[name] = {
            "accuracy": acc,
            "precision": prec,
            "recall": rec,
            "f1": f1,
            "roc_auc": auc,
            "train_time": train_time,
            "confusion_matrix": cm,
            "model": model
        }

        print(f"  Training time: {train_time:.2f}s")
        print(f"  Accuracy:      {acc*100:.2f}%")
        print(f"  Precision:     {prec*100:.2f}%")
        print(f"  Recall:        {rec*100:.2f}%")
        print(f"  F1-Score:      {f1*100:.2f}%")
        print(f"  ROC-AUC:       {auc:.4f}")
        print(f"  Confusion Matrix (TN={cm[0,0]}, FP={cm[0,1]}, FN={cm[1,0]}, TP={cm[1,1]}):")
        print(f"    [[{cm[0,0]:5d} (TN), {cm[0,1]:5d} (FP)]")
        print(f"     [{cm[1,0]:5d} (FN), {cm[1,1]:5d} (TP)]]")

        if f1 > best_f1:
            best_f1 = f1
            champion_name = name
            champion_model = model

    print("\n" + "=" * 70)
    print(f" Champion Model Selected: {champion_name} (F1-Score: {best_f1*100:.2f}%)")
    print("=" * 70)

    # 5. Save Artifacts for FastAPI inference
    print(f"\nSerializing champion model to: {MODEL_OUTPUT_PATH}")
    joblib.dump(champion_model, str(MODEL_OUTPUT_PATH), compress=3)

    print(f"Serializing TF-IDF vectorizer to: {VECTORIZER_OUTPUT_PATH}")
    joblib.dump(vectorizer, str(VECTORIZER_OUTPUT_PATH), compress=3)

    model_size_mb = MODEL_OUTPUT_PATH.stat().st_size / (1024 * 1024)
    vec_size_mb = VECTORIZER_OUTPUT_PATH.stat().st_size / (1024 * 1024)
    print(f"Model artifact size:      {model_size_mb:.2f} MB")
    print(f"Vectorizer artifact size: {vec_size_mb:.2f} MB")

    # 6. Test inference verification
    print("\nRunning Inference Verification Tests:")
    test_cases = [
        ("Good morning, I am calling from the library regarding the book you reserved. It is ready for pickup.", False),
        ("URGENT: This is Officer Miller from Police Department. Your bank account is locked for money laundering. Transfer 50000 rupees immediately to verify.", True),
        ("Hello, this is customer service from Amazon. Your package was delivered today. Thank you.", False),
        ("Your credit card is compromised. Please share your OTP and CVV right now to block the transaction or police will be sent.", True),
    ]

    for sample_text, expected_scam in test_cases:
        feat = vectorizer.transform([sample_text])
        prob = float(champion_model.predict_proba(feat)[0, 1])
        pred_label = "SCAM" if prob >= 0.5 else "LEGIT"
        expected_label = "SCAM" if expected_scam else "LEGIT"
        status = "PASS" if pred_label == expected_label else "FAIL"
        print(f"  [{status}] Prob={prob*100:.1f}% -> Pred: {pred_label:5s} | Expected: {expected_label:5s} | '{sample_text[:65]}...'")

    print("\nTraining and validation successfully completed!")

if __name__ == "__main__":
    train_and_evaluate()
