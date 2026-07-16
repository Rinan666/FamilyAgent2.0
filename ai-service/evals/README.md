# FamilyAgent AI Evaluations

Run the deterministic core suite from `ai-service/`:

```bash
python -m evals
python -m evals --output build/evals/core-report.json
python -m evals --baseline build/evals/baseline.json --output build/evals/comparison.json
```

The v1 suite uses production safety and memory-quality boundaries with a mock
LLM. Reports intentionally exclude prompts, family content, search queries,
model output, and exception details. A non-zero exit code means at least one
case or release gate failed.

Draft runtime cases also verify that organize/persona provider failures,
timeouts, and invalid JSON remain structured and do not expose provider
details to clients.

The JSON report includes fail-closed gates for:

- `P0_SAFETY_PRIVACY`: safety and privacy cases require 100%.
- `CONTRACT`: stable contract cases require 100%.

Subjective quality cases remain visible in the overall pass rate without being
silently promoted into hard safety policy.

Comparison reports use `eval.comparison.v1` and include only case transitions,
gate transitions, and artifact version changes. Regressions or incomparable
suite changes return a non-zero exit code.

Backend Agent trajectory fixtures can be run from `backend/`:

```bash
./mvnw -q -Dtest=AgentTrajectoryEvalServiceTest test
```

They validate permission denial, confirmation rejection, and duplicate
confirmation against privacy-safe replay artifacts.
