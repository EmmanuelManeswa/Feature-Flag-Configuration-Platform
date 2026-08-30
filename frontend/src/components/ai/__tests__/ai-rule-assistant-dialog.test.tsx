import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQueryClient } from "@/test/render";
import { AiRuleAssistantDialog } from "@/components/ai/ai-rule-assistant-dialog";
import * as aiApi from "@/features/ai/api";
import { ApiError } from "@/lib/api-client";
import type { RuleProposalDto } from "@/types/api";

describe("AiRuleAssistantDialog", () => {
  it("shows the AI-generated proposal, clearly labeled, and calls onApply when accepted", async () => {
    const proposal: RuleProposalDto = {
      strategy: "PERCENTAGE_ROLLOUT",
      rolloutPercentage: 20,
      rules: [
        { attribute: "location", operator: "EQUALS", value: "Harare" },
        { attribute: "userType", operator: "NOT_EQUALS", value: "INTERNAL_STAFF" },
      ],
      explanation: "Enable for 20% of users in Harare, excluding internal staff.",
    };
    vi.spyOn(aiApi, "generateRuleProposal").mockResolvedValue(proposal);
    const onApply = vi.fn();

    renderWithQueryClient(<AiRuleAssistantDialog open onOpenChange={() => {}} onApply={onApply} />);

    await userEvent.type(
      screen.getByPlaceholderText(/enable this for 20%/i),
      "enable this for 20% of users in Harare except internal staff",
    );
    await userEvent.click(screen.getByRole("button", { name: /generate proposal/i }));

    await waitFor(() => expect(screen.getByText(/AI-generated — review before applying/i)).toBeInTheDocument());
    expect(screen.getByText("PERCENTAGE_ROLLOUT")).toBeInTheDocument();
    expect(screen.getByText(/excluding internal staff/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /apply proposal/i }));
    expect(onApply).toHaveBeenCalledWith(proposal);
  });

  it("shows a friendly message and no proposal when the AI is unavailable", async () => {
    vi.spyOn(aiApi, "generateRuleProposal").mockRejectedValue(
      new ApiError(
        503,
        {
          type: "urn:problem-type:ai-unavailable",
          title: "AI unavailable",
          status: 503,
          detail: "Unable to generate a rule proposal right now. You can configure the rule manually.",
          instance: "/api/v1/ai/rule-proposals",
          correlationId: "test-correlation-id",
        },
        "AI unavailable",
      ),
    );

    renderWithQueryClient(<AiRuleAssistantDialog open onOpenChange={() => {}} onApply={vi.fn()} />);

    await userEvent.type(screen.getByPlaceholderText(/enable this for 20%/i), "enable this for everyone");
    await userEvent.click(screen.getByRole("button", { name: /generate proposal/i }));

    await waitFor(() => expect(screen.getByText(/unable to generate a rule proposal/i)).toBeInTheDocument());
    // Never shows a proposal card, and never shows the duplicated
    // "configure manually" copy (a real bug caught by manual testing —
    // see the frontend feature-pages commit).
    expect(screen.queryByText(/AI-generated/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/manually instead/i)).not.toBeInTheDocument();
  });

  it("requires non-empty input before Generate proposal is enabled", () => {
    renderWithQueryClient(<AiRuleAssistantDialog open onOpenChange={() => {}} onApply={vi.fn()} />);
    expect(screen.getByRole("button", { name: /generate proposal/i })).toBeDisabled();
  });
});
