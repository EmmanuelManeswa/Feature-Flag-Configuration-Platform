"use client";

import { useEffect, useState } from "react";
import { useForm } from "@tanstack/react-form";
import { Plus, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FormFieldWrapper } from "@/components/form-field";
import { useEnvironments } from "@/features/environments/hooks";
import { useCreateFlag, useUpdateFlag } from "@/features/flags/hooks";
import { flagFields, targetingRuleFields } from "@/features/flags/schemas";
import { ApiError } from "@/lib/api-client";
import type { FeatureFlagDto, FlagType, RuleProposalDto, TargetingOperator } from "@/types/api";

interface FlagFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  mode: "create" | "edit";
  flag?: FeatureFlagDto;
  defaultEnvironmentId?: string;
  /** Pre-fills the form from an AI proposal (still requires the human to review/submit). */
  aiProposal?: RuleProposalDto;
}

interface FormValues {
  key: string;
  name: string;
  description: string;
  environmentId: string;
  type: FlagType;
  enabled: boolean;
  rolloutPercentage: number;
  targetingRules: { attribute: string; operator: TargetingOperator; value: string }[];
}

export function FlagFormDialog({ open, onOpenChange, mode, flag, defaultEnvironmentId, aiProposal }: FlagFormDialogProps) {
  const { data: environments } = useEnvironments();
  const createFlag = useCreateFlag();
  const updateFlag = useUpdateFlag(flag?.id ?? "");
  const [staleConflict, setStaleConflict] = useState(false);

  const isEdit = mode === "edit" && Boolean(flag);
  const isPending = createFlag.isPending || updateFlag.isPending;

  const form = useForm({
    defaultValues: {
      key: flag?.key ?? "",
      // The AI proposes a strategy/rollout/targeting shape, not a flag name —
      // dumping its explanation text into the name field read as an
      // unfinished-looking default. Leave it for the human to name.
      name: flag?.name ?? "",
      description: flag?.description ?? "",
      environmentId: flag?.environmentId ?? defaultEnvironmentId ?? "",
      type: flag?.type ?? aiProposal?.strategy ?? "BOOLEAN",
      enabled: flag?.enabled ?? true,
      rolloutPercentage: flag?.rolloutPercentage ?? aiProposal?.rolloutPercentage ?? 50,
      targetingRules: flag?.targetingRules ?? aiProposal?.rules ?? [],
    } satisfies FormValues,
    onSubmit: async ({ value }) => {
      setStaleConflict(false);
      try {
        const targetingRules = value.targetingRules;
        if (isEdit && flag) {
          await updateFlag.mutateAsync({
            name: value.name,
            description: value.description || null,
            enabled: value.enabled,
            rolloutPercentage: value.type === "PERCENTAGE_ROLLOUT" ? value.rolloutPercentage : null,
            targetingRules,
            expectedVersion: flag.version,
          });
          toast.success("Flag updated", { description: `${value.name} saved.` });
        } else {
          await createFlag.mutateAsync({
            key: value.key,
            name: value.name,
            description: value.description || null,
            environmentId: value.environmentId,
            type: value.type,
            enabled: value.enabled,
            rolloutPercentage: value.type === "PERCENTAGE_ROLLOUT" ? value.rolloutPercentage : null,
            targetingRules,
          });
          toast.success("Flag created", { description: `${value.name} was created.` });
        }
        onOpenChange(false);
      } catch (error) {
        if (error instanceof ApiError && error.status === 409) {
          setStaleConflict(true);
          toast.error("This flag was modified by another user", {
            description: "Close this dialog and reopen it to load the latest version before saving your changes.",
          });
          return;
        }
        const message = error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
        toast.error(isEdit ? "Update failed" : "Create failed", { description: message });
      }
    },
  });

  // Reset the form whenever a different flag (or a fresh AI proposal) is opened.
  useEffect(() => {
    if (open) {
      form.reset();
      setStaleConflict(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, flag?.id, aiProposal]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{isEdit ? `Edit ${flag?.name}` : "Create feature flag"}</DialogTitle>
          <DialogDescription>
            {isEdit
              ? `Version ${flag?.version} · ${flag?.environmentName}`
              : "Flags are scoped to a single environment and evaluated deterministically."}
          </DialogDescription>
        </DialogHeader>

        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            void form.handleSubmit();
          }}
        >
          {!isEdit && (
            <div className="grid grid-cols-2 gap-3">
              <form.Field name="key" validators={{ onBlur: flagFields.key }}>
                {(field) => (
                  <FormFieldWrapper
                    label="Key"
                    htmlFor={field.name}
                    error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
                  >
                    <Input
                      id={field.name}
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(e) => field.handleChange(e.target.value)}
                      placeholder="new-dashboard"
                      className="font-mono text-sm"
                    />
                  </FormFieldWrapper>
                )}
              </form.Field>

              <form.Field name="environmentId" validators={{ onBlur: flagFields.environmentId }}>
                {(field) => (
                  <FormFieldWrapper
                    label="Environment"
                    htmlFor={field.name}
                    error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
                  >
                    <Select value={field.state.value} onValueChange={field.handleChange}>
                      <SelectTrigger id={field.name} className="w-full">
                        <SelectValue placeholder="Select..." />
                      </SelectTrigger>
                      <SelectContent>
                        {environments?.map((env) => (
                          <SelectItem key={env.id} value={env.id}>
                            {env.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormFieldWrapper>
                )}
              </form.Field>
            </div>
          )}

          <form.Field name="name" validators={{ onBlur: flagFields.name }}>
            {(field) => (
              <FormFieldWrapper
                label="Name"
                htmlFor={field.name}
                error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
              >
                <Input
                  id={field.name}
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(e) => field.handleChange(e.target.value)}
                  placeholder="New Dashboard"
                />
              </FormFieldWrapper>
            )}
          </form.Field>

          <form.Field name="description">
            {(field) => (
              <FormFieldWrapper label="Description" htmlFor={field.name}>
                <Textarea
                  id={field.name}
                  value={field.state.value}
                  onChange={(e) => field.handleChange(e.target.value)}
                  rows={2}
                  placeholder="Optional"
                />
              </FormFieldWrapper>
            )}
          </form.Field>

          {!isEdit && (
            <form.Field name="type">
              {(field) => (
                <FormFieldWrapper label="Strategy" htmlFor={field.name}>
                  <Select value={field.state.value} onValueChange={(v) => field.handleChange(v as FlagType)}>
                    <SelectTrigger id={field.name} className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="BOOLEAN">Boolean (on/off)</SelectItem>
                      <SelectItem value="PERCENTAGE_ROLLOUT">Percentage rollout</SelectItem>
                    </SelectContent>
                  </Select>
                </FormFieldWrapper>
              )}
            </form.Field>
          )}

          <form.Subscribe selector={(state) => state.values.type}>
            {(type) =>
              type === "PERCENTAGE_ROLLOUT" ? (
                <form.Field name="rolloutPercentage" validators={{ onBlur: flagFields.rolloutPercentage }}>
                  {(field) => (
                    <FormFieldWrapper
                      label="Rollout percentage"
                      htmlFor={field.name}
                      error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
                      description="Deterministic: the same user always lands on the same side of this line."
                    >
                      <Input
                        id={field.name}
                        type="number"
                        min={0}
                        max={100}
                        value={field.state.value}
                        onBlur={field.handleBlur}
                        onChange={(e) => field.handleChange(Number(e.target.value))}
                      />
                    </FormFieldWrapper>
                  )}
                </form.Field>
              ) : null
            }
          </form.Subscribe>

          <form.Field name="enabled">
            {(field) => (
              <div className="flex items-center justify-between rounded-md border border-border px-3 py-2.5">
                <div>
                  <Label htmlFor={field.name}>Enabled</Label>
                  <p className="text-xs text-muted-foreground">Whether this flag is active at all.</p>
                </div>
                <Switch id={field.name} checked={field.state.value} onCheckedChange={field.handleChange} />
              </div>
            )}
          </form.Field>

          <form.Field name="targetingRules" mode="array">
            {(field) => (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label>Targeting rules</Label>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => field.pushValue({ attribute: "", operator: "EQUALS", value: "" })}
                  >
                    <Plus className="size-3.5" />
                    Add rule
                  </Button>
                </div>
                {field.state.value.length === 0 ? (
                  <p className="text-xs text-muted-foreground">No targeting rules — applies to all matching users.</p>
                ) : (
                  <div className="space-y-2">
                    {field.state.value.map((_, index) => (
                      <div key={index} className="flex items-start gap-2">
                        <form.Field name={`targetingRules[${index}].attribute`} validators={{ onBlur: targetingRuleFields.attribute }}>
                          {(sub) => (
                            <Input
                              value={sub.state.value}
                              onBlur={sub.handleBlur}
                              onChange={(e) => sub.handleChange(e.target.value)}
                              placeholder="attribute"
                              className="font-mono text-xs"
                            />
                          )}
                        </form.Field>
                        <form.Field name={`targetingRules[${index}].operator`}>
                          {(sub) => (
                            <Select value={sub.state.value} onValueChange={(v) => sub.handleChange(v as TargetingOperator)}>
                              <SelectTrigger className="w-[120px] shrink-0">
                                <SelectValue />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="EQUALS">EQUALS</SelectItem>
                                <SelectItem value="NOT_EQUALS">NOT_EQUALS</SelectItem>
                              </SelectContent>
                            </Select>
                          )}
                        </form.Field>
                        <form.Field name={`targetingRules[${index}].value`} validators={{ onBlur: targetingRuleFields.value }}>
                          {(sub) => (
                            <Input
                              value={sub.state.value}
                              onBlur={sub.handleBlur}
                              onChange={(e) => sub.handleChange(e.target.value)}
                              placeholder="value"
                              className="text-xs"
                            />
                          )}
                        </form.Field>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          className="shrink-0 text-muted-foreground hover:text-destructive"
                          onClick={() => field.removeValue(index)}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </form.Field>

          {staleConflict && (
            <p className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
              This configuration was modified by another user. Close this dialog and reopen it to load the current
              version before saving your changes.
            </p>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <form.Subscribe selector={(state) => state.canSubmit}>
              {(canSubmit) => (
                <Button type="submit" disabled={!canSubmit || isPending}>
                  {isPending ? "Saving..." : isEdit ? "Save changes" : "Create flag"}
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
