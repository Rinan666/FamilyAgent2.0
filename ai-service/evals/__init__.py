"""Low-cost regression evaluations for FamilyAgent AI boundaries."""

from .cases import default_golden_cases
from .runner import EvalRunner

__all__ = ["EvalRunner", "default_golden_cases"]
