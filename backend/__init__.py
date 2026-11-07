"""
Scam Shield Backend Package
"""
import sys
from pathlib import Path

# Automatically inject repository root into sys.path
_repo_root = str(Path(__file__).resolve().parent.parent)
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

