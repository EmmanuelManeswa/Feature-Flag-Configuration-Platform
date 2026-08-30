import { apiFetch } from "@/lib/api-client";
import type { RuleProposalDto } from "@/types/api";

export function generateRuleProposal(naturalLanguageRequest: string): Promise<RuleProposalDto> {
  return apiFetch<RuleProposalDto>("/api/v1/ai/rule-proposals", {
    method: "POST",
    body: { naturalLanguageRequest },
  });
}
