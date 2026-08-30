# ADR-003: AI provider abstraction, model choice, and the validation pipeline

**Status:** Accepted

## Context

The assessment requires an AI "rule assistant" that turns natural language
("enable this for 20% of users in Harare except internal staff") into a
structured targeting rule proposal — and is explicit that the AI must only
ever *propose*; a human reviews and explicitly saves. It also requires the
feature to fail gracefully and to be demonstrable without a paid API key.

## Decision: two-layer abstraction

```
AiRuleController -> AiRuleAssistantService -> AiProvider
```

- **`AiProvider`** (`ai.provider`): the narrowest possible seam —
  `String complete(String systemPrompt, String userPrompt)`. Knows nothing
  about feature flags, JSON schemas, or targeting rules. Two implementations,
  selected by `app.ai.provider` (`mock` | `docker-model-runner`):
  - `MockAiProvider` — the **default**. No network call, no dependency,
    always available. Not a stub that returns a canned single response — a
    small deterministic keyword parser (percentage, location, department,
    "except internal staff") that handles the assessment's own example
    precisely and falls back to a plain BOOLEAN proposal (explaining why)
    when it can't confidently extract a percentage. This is what makes the
    AI feature demonstrable with zero setup, per the assessment's explicit
    requirement.
  - `DockerModelRunnerAiProvider` — calls a local model through Docker Model
    Runner's OpenAI-compatible `/chat/completions` endpoint via the JDK's
    own `java.net.http.HttpClient` (no WebClient/OkHttp dependency needed
    for one non-streaming call). Free, no API key, nothing leaves the
    machine.
- **`AiRuleAssistantService`** (`ai.service`): the one place that knows the
  domain shape. Builds the prompt, calls `AiProvider`, and runs every
  response — mock or real model — through the identical pipeline:
  1. Extract JSON from the raw text (handles a model wrapping its answer in
     prose or a markdown code fence, tracking string/escape state so braces
     inside an explanation string don't break the extraction).
  2. Deserialize into `RuleProposalDto`.
  3. Bean Validation — and critically, `RuleProposalDto.rules` is typed as
     `List<TargetingRuleDto>`, the *exact same type* a human-submitted flag's
     targeting rules are validated as (see `featureflag.dto.TargetingRuleDto`).
     This is what makes "AI output is validated exactly like human input" a
     fact about the code, not a policy that has to be remembered.
  4. Domain-invariant validation (the same BOOLEAN-vs-PERCENTAGE_ROLLOUT
     rule `FeatureFlagService` enforces).
  5. Any failure at any step — provider unreachable, malformed JSON, failed
     schema validation, failed domain validation — collapses to the single
     `AiUnavailableException`, mapped to `503` with the assessment's own
     failure copy ("Unable to generate a rule proposal right now. You can
     configure the rule manually."). The *specific* reason is always logged
     server-side with the correlation ID; the client never learns which
     internal step failed.

The endpoint (`POST /api/v1/ai/rule-proposals`) **never persists anything**.
Applying a proposal means the frontend pre-fills the normal create/update
flag form with the proposal's fields and a human explicitly submits it
through `FeatureFlagService` — the same validation, the same audit trail,
the same optimistic-concurrency check as any manually-authored change. There
is no code path from "the model said so" to a saved flag.

## Model choice: `ai/llama3.2` (2.02GB, llama.cpp backend)

Evaluated against Docker's model catalog (`docker model search`) on the
criteria the assessment asks for:

- **Suitability for the actual task** — this is a narrow, single-turn
  "extract a percentage, a location, an exclusion, and describe it as JSON"
  task, not open-ended reasoning or code generation. A 2B-class instruction-
  tuned model is well within its competence for this; nothing here needs a
  7B+ model's extra capability.
- **Latency / resource footprint** — 2.02GB downloads and loads
  meaningfully faster than the 4.68GB `ai/qwen2.5` or the 5GB+ `ai/qwen3`
  family, and runs comfortably on a laptop GPU/CPU without competing hard
  for RAM against everything else already running locally.
- **Instruction following** — Llama 3.2's instruct-tuned variant is
  specifically documented as reliable for structured chat/coding-adjacent
  tasks, which is the closest published signal to "reliably emit one JSON
  object and nothing else."
- **Reproducibility** — `docker/llama3.2` is an official Docker-published
  model in the catalog (not a third-party HuggingFace mirror), so `docker
  model pull ai/llama3.2` is a stable, documented command a reviewer can run
  themselves.

Explicitly **not** the largest available option (`ai/qwen3.5` at 22.9GB,
`ai/qwen3-coder-next` at 48GB) — per the assessment's own guidance ("do not
blindly choose the largest model"), and because this task has no need for
that capability. If structured-output reliability against Llama 3.2 turned
out to be poor in practice, `ai/qwen2.5` (still under 5GB, Qwen models are
generally strong at structured JSON) would be the next thing to try — the
provider abstraction makes that a one-line config change
(`AI_MODEL=ai/qwen2.5`), not a code change.

## Why `MockAiProvider` stays the *default*, not `DockerModelRunnerAiProvider`

Even with Docker Model Runner running locally, the mock stays the default
(`app.ai.provider=mock` in `.env.example`) so that:
- The reviewer's first `docker compose up` / local run works identically
  whether or not they have Docker Model Runner enabled or a model pulled.
- CI and automated tests never depend on a multi-gigabyte model download or
  GPU/CPU inference time.
- The AI feature is still fully demonstrable — the mock isn't a "fake" mode,
  it's a genuine (if narrower) implementation of the same interface.

Setting `AI_PROVIDER=docker-model-runner` after pulling a model swaps in the
real thing with no other code or config change — that's the entire point of
the abstraction.

## Security

- No secrets, JWTs, or internal identifiers are ever included in a prompt —
  the only user-controlled input that reaches the model is the natural-
  language request text itself.
- The system prompt explicitly instructs the model to treat the user's
  message as data to extract from, not as instructions to itself, and to
  ignore any attempt within it to override these instructions, reveal them,
  or claim to be a system message — a direct mitigation for prompt
  injection via the natural-language input field.
- `operator` is a closed enum (`EQUALS`/`NOT_EQUALS`) — Jackson itself
  rejects any other value at deserialization time, before validation even
  runs. `attribute` must match a plain-identifier pattern. `rolloutPercentage`
  is bounded `0-100`. None of this is optional or best-effort; it's the same
  Bean Validation annotations a human-submitted request goes through.

## What was rejected

- **Trusting the model's JSON directly** — explicitly disallowed by the
  assessment ("AI output is trusted as authoritative without validation" is
  listed as an automatic deduction) and by `.claude/CLAUDE.md`.
- **A single combined `AiProvider` that also knows the `RuleProposal` shape**
  — would make swapping providers (mock/Docker Model Runner/a future OpenAI
  or Anthropic provider) require duplicating the prompt-building and
  validation logic in every implementation instead of writing it once.
