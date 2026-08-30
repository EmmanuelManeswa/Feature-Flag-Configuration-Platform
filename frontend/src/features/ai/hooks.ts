import { useMutation } from "@tanstack/react-query";
import { generateRuleProposal } from "./api";

export function useGenerateRuleProposal() {
  return useMutation({
    mutationFn: (naturalLanguageRequest: string) => generateRuleProposal(naturalLanguageRequest),
  });
}
