import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQueryClient } from "@/test/render";
import { EvaluationPlayground } from "@/components/evaluation/evaluation-playground";
import * as flagsApi from "@/features/flags/api";
import type { EvaluationResultDto } from "@/types/api";

describe("EvaluationPlayground", () => {
  it("shows the resolved result, reason, and bucket after a successful evaluation", async () => {
    const result: EvaluationResultDto = {
      value: true,
      reason: "ROLLOUT_INCLUDED",
      bucket: 15,
      unmatchedRule: null,
      flagKey: "ai-assistant",
      environmentName: "PROD",
      cacheHit: false,
      evaluationLatencyMicros: 4200,
    };
    vi.spyOn(flagsApi, "evaluateFlag").mockResolvedValue(result);

    renderWithQueryClient(<EvaluationPlayground flagId="flag-1" />);

    await userEvent.click(screen.getByRole("button", { name: /evaluate/i }));

    await waitFor(() => expect(screen.getByText(/TRUE — feature shown/i)).toBeInTheDocument());
    expect(screen.getByText(/inside the rollout bucket/i)).toBeInTheDocument();
    expect(screen.getByText("15 / 100")).toBeInTheDocument();
    expect(screen.getByText(/cache miss/i)).toBeInTheDocument();
  });

  it("shows the unmatched targeting rule when a rule excludes the user", async () => {
    const result: EvaluationResultDto = {
      value: false,
      reason: "TARGETING_RULE_NOT_MATCHED",
      bucket: null,
      unmatchedRule: { attribute: "userType", operator: "NOT_EQUALS", value: "INTERNAL_STAFF" },
      flagKey: "ai-assistant",
      environmentName: "PROD",
      cacheHit: true,
      evaluationLatencyMicros: 800,
    };
    vi.spyOn(flagsApi, "evaluateFlag").mockResolvedValue(result);

    renderWithQueryClient(<EvaluationPlayground flagId="flag-1" />);
    await userEvent.click(screen.getByRole("button", { name: /evaluate/i }));

    await waitFor(() => expect(screen.getByText(/FALSE — feature hidden/i)).toBeInTheDocument());
    expect(screen.getByText(/userType.*!=.*INTERNAL_STAFF/)).toBeInTheDocument();
  });

  it("disables the Evaluate button while the request is in flight", async () => {
    let resolveRequest: (value: EvaluationResultDto) => void = () => {};
    vi.spyOn(flagsApi, "evaluateFlag").mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }),
    );

    renderWithQueryClient(<EvaluationPlayground flagId="flag-1" />);
    await userEvent.click(screen.getByRole("button", { name: /evaluate/i }));

    expect(screen.getByRole("button", { name: /evaluating/i })).toBeDisabled();

    resolveRequest({
      value: true,
      reason: "BOOLEAN_MATCH",
      bucket: null,
      unmatchedRule: null,
      flagKey: "x",
      environmentName: "DEV",
      cacheHit: false,
      evaluationLatencyMicros: 100,
    });
    await waitFor(() => expect(screen.getByRole("button", { name: /^evaluate$/i })).not.toBeDisabled());
  });
});
