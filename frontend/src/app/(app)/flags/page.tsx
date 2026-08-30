"use client";

import { useState } from "react";
import { Plus, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { DataTable } from "@/components/data-table";
import { flagsColumns } from "@/components/flags/flags-columns";
import { FlagFormDialog } from "@/components/flags/flag-form-dialog";
import { AiRuleAssistantDialog } from "@/components/ai/ai-rule-assistant-dialog";
import { useFlags } from "@/features/flags/hooks";
import { useEnvironments } from "@/features/environments/hooks";
import { useAuth } from "@/providers/auth-provider";
import type { RuleProposalDto } from "@/types/api";

export default function FlagsPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";

  const [environmentId, setEnvironmentId] = useState<string>("all");
  const [page, setPage] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [aiProposal, setAiProposal] = useState<RuleProposalDto | undefined>();

  const { data: environments } = useEnvironments();
  const flagsQuery = useFlags({
    environmentId: environmentId === "all" ? undefined : environmentId,
    page,
  });

  function handleFilterChange(value: string) {
    setEnvironmentId(value);
    setPage(0);
  }

  function handleApplyProposal(proposal: RuleProposalDto) {
    setAiProposal(proposal);
    setAiOpen(false);
    setCreateOpen(true);
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Feature Flags</h1>
          <p className="text-sm text-muted-foreground">Manage rollout strategy and targeting across environments.</p>
        </div>
        {isAdmin && (
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setAiOpen(true)}>
              <Sparkles className="size-4 text-brand-violet" />
              AI Rule Assistant
            </Button>
            <Button
              onClick={() => {
                setAiProposal(undefined);
                setCreateOpen(true);
              }}
            >
              <Plus className="size-4" />
              Create flag
            </Button>
          </div>
        )}
      </div>

      <div className="flex items-center gap-2">
        <Select value={environmentId} onValueChange={handleFilterChange}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="All environments" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All environments</SelectItem>
            {environments?.map((env) => (
              <SelectItem key={env.id} value={env.id}>
                {env.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <DataTable
        columns={flagsColumns}
        data={flagsQuery.data?.content ?? []}
        isLoading={flagsQuery.isLoading}
        emptyMessage="No feature flags yet. Create one to get started."
        page={page}
        totalPages={flagsQuery.data?.totalPages ?? 0}
        onPageChange={setPage}
      />

      {isAdmin && (
        <>
          <FlagFormDialog
            open={createOpen}
            onOpenChange={setCreateOpen}
            mode="create"
            defaultEnvironmentId={environmentId === "all" ? undefined : environmentId}
            aiProposal={aiProposal}
          />
          <AiRuleAssistantDialog open={aiOpen} onOpenChange={setAiOpen} onApply={handleApplyProposal} />
        </>
      )}
    </div>
  );
}
