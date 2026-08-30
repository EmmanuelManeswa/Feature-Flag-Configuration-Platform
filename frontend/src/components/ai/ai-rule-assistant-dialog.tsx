"use client";

import { useState } from "react";
import { AlertTriangle, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useGenerateRuleProposal } from "@/features/ai/hooks";
import { ApiError } from "@/lib/api-client";
import type { RuleProposalDto } from "@/types/api";

const EXAMPLE = "Enable this for 20% of users in Harare except internal staff";

export function AiRuleAssistantDialog({
  open,
  onOpenChange,
  onApply,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onApply: (proposal: RuleProposalDto) => void;
}) {
  const [text, setText] = useState("");
  const generate = useGenerateRuleProposal();

  function handleGenerate() {
    if (!text.trim()) return;
    generate.mutate(text.trim());
  }

  function handleOpenChange(next: boolean) {
    if (!next) {
      setText("");
      generate.reset();
    }
    onOpenChange(next);
  }

  const errorMessage =
    generate.error instanceof ApiError
      ? generate.error.message
      : generate.isError
        ? "Unable to generate a rule proposal right now. You can configure the rule manually."
        : null;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="size-4 text-brand-violet" />
            AI Rule Assistant
          </DialogTitle>
          <DialogDescription>
            Describe the rollout in plain English. The proposal below is <strong>never</strong> saved automatically —
            review it, then apply it to pre-fill the create-flag form.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-3">
          <Textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder={EXAMPLE}
            rows={3}
            disabled={generate.isPending}
          />
          <Button onClick={handleGenerate} disabled={!text.trim() || generate.isPending} className="w-full">
            {generate.isPending ? "Generating..." : "Generate proposal"}
          </Button>

          {errorMessage && (
            <Alert variant="destructive">
              <AlertTriangle className="size-4" />
              <AlertTitle>AI unavailable</AlertTitle>
              {/* The 503 detail from the backend already ends with "You can
                  configure the rule manually." — not appended again here. */}
              <AlertDescription>{errorMessage}</AlertDescription>
            </Alert>
          )}

          {generate.data && (
            <div className="space-y-3 rounded-lg border border-brand-violet/25 bg-brand-violet/5 p-4">
              <div className="flex items-center justify-between">
                <Badge className="bg-brand-violet text-brand-violet-foreground">AI-generated — review before applying</Badge>
              </div>
              <dl className="grid grid-cols-2 gap-2 text-sm">
                <dt className="text-muted-foreground">Strategy</dt>
                <dd className="font-mono text-xs">{generate.data.strategy}</dd>
                {generate.data.rolloutPercentage !== null && (
                  <>
                    <dt className="text-muted-foreground">Rollout</dt>
                    <dd className="font-mono text-xs">{generate.data.rolloutPercentage}%</dd>
                  </>
                )}
              </dl>
              {generate.data.rules.length > 0 && (
                <div className="space-y-1">
                  <p className="text-xs font-medium text-muted-foreground">Targeting rules</p>
                  <ul className="space-y-1">
                    {generate.data.rules.map((rule, i) => (
                      <li key={i} className="font-mono text-xs">
                        {rule.attribute} {rule.operator === "EQUALS" ? "=" : "!="} &quot;{rule.value}&quot;
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              <p className="text-sm text-foreground/90">{generate.data.explanation}</p>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)}>
            {generate.data ? "Discard" : "Cancel"}
          </Button>
          {generate.data && <Button onClick={() => onApply(generate.data)}>Apply proposal</Button>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
