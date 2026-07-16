"""Command-line entry point for the low-cost FamilyAgent eval suite."""

import argparse
import asyncio
from pathlib import Path

from .cases import default_golden_cases
from .comparison import EvalReportComparator
from .comparison_models import EvalComparisonConclusion
from .models import EvalReport
from .runner import EvalRunner


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run FamilyAgent deterministic AI evaluations")
    parser.add_argument("--output", type=Path, help="Optional JSON report path")
    parser.add_argument("--baseline", type=Path, help="Compare the current run with a prior JSON report")
    return parser.parse_args()


async def _run() -> int:
    args = _arguments()
    report = await EvalRunner().run(default_golden_cases())
    output = report
    comparison_passed = True
    if args.baseline:
        baseline = EvalReport.model_validate_json(args.baseline.read_text(encoding="utf-8"))
        output = EvalReportComparator().compare(baseline, report)
        comparison_passed = output.conclusion in {
            EvalComparisonConclusion.NO_CHANGE,
            EvalComparisonConclusion.IMPROVED,
        }
    payload = output.model_dump_json(indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload + "\n", encoding="utf-8")
    else:
        print(payload)
    gates_passed = all(gate.passed for gate in report.gates)
    return 0 if report.metrics.failed_count == 0 and gates_passed and comparison_passed else 1


if __name__ == "__main__":
    raise SystemExit(asyncio.run(_run()))
