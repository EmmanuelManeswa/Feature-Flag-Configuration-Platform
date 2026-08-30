"use client";

import { useState } from "react";
import { Plus, Trash2, Zap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { useEvaluateFlag } from "@/features/flags/hooks";
import { cn } from "@/lib/utils";

const REASON_LABELS: Record<string, string> = {
  FLAG_DISABLED: "Flag is disabled",
  TARGETING_RULE_NOT_MATCHED: "A targeting rule did not match",
  BOOLEAN_MATCH: "Boolean flag, enabled, rules matched",
  ROLLOUT_INCLUDED: "Inside the rollout bucket",
  ROLLOUT_EXCLUDED: "Outside the rollout bucket",
};

export function EvaluationPlayground({ flagId }: { flagId: string }) {
  const [stableIdentifier, setStableIdentifier] = useState("user-123");
  const [attributes, setAttributes] = useState<{ key: string; value: string }[]>([
    { key: "location", value: "Harare" },
    { key: "userType", value: "EXTERNAL" },
  ]);
  const evaluate = useEvaluateFlag(flagId);

  function updateAttribute(index: number, field: "key" | "value", value: string) {
    setAttributes((prev) => prev.map((attr, i) => (i === index ? { ...attr, [field]: value } : attr)));
  }

  function handleEvaluate() {
    const attributeRecord = Object.fromEntries(
      attributes.filter((a) => a.key.trim()).map((a) => [a.key.trim(), a.value]),
    );
    evaluate.mutate({ stableIdentifier, attributes: attributeRecord });
  }

  const result = evaluate.data;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Zap className="size-4" />
          Evaluation playground
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="stableIdentifier">Stable identifier (user ID / email)</Label>
          <Input id="stableIdentifier" value={stableIdentifier} onChange={(e) => setStableIdentifier(e.target.value)} />
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <Label>Attributes</Label>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setAttributes((prev) => [...prev, { key: "", value: "" }])}
            >
              <Plus className="size-3.5" />
              Add attribute
            </Button>
          </div>
          <div className="space-y-2">
            {attributes.map((attr, index) => (
              <div key={index} className="flex items-center gap-2">
                <Input
                  placeholder="attribute (e.g. department)"
                  value={attr.key}
                  onChange={(e) => updateAttribute(index, "key", e.target.value)}
                  className="font-mono text-xs"
                />
                <Input
                  placeholder="value"
                  value={attr.value}
                  onChange={(e) => updateAttribute(index, "value", e.target.value)}
                  className="text-xs"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="shrink-0 text-muted-foreground hover:text-destructive"
                  onClick={() => setAttributes((prev) => prev.filter((_, i) => i !== index))}
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
            ))}
          </div>
        </div>

        <Button onClick={handleEvaluate} disabled={!stableIdentifier.trim() || evaluate.isPending} className="w-full">
          {evaluate.isPending ? "Evaluating..." : "Evaluate"}
        </Button>

        {result && (
          <>
            <Separator />
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span
                  className={cn(
                    "rounded-md px-3 py-1.5 text-sm font-semibold",
                    result.value ? "bg-success/15 text-success" : "bg-muted text-muted-foreground",
                  )}
                >
                  {result.value ? "TRUE — feature shown" : "FALSE — feature hidden"}
                </span>
                <Badge variant="outline" className="text-xs">
                  {result.evaluationLatencyMicros / 1000 < 1
                    ? `${result.evaluationLatencyMicros}µs`
                    : `${(result.evaluationLatencyMicros / 1000).toFixed(2)}ms`}{" "}
                  · {result.cacheHit ? "cache hit" : "cache miss"}
                </Badge>
              </div>

              <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
                <dt className="text-muted-foreground">Reason</dt>
                <dd>{REASON_LABELS[result.reason] ?? result.reason}</dd>

                {result.bucket !== null && (
                  <>
                    <dt className="text-muted-foreground">Bucket</dt>
                    <dd className="font-mono">{result.bucket} / 100</dd>
                  </>
                )}

                {result.unmatchedRule && (
                  <>
                    <dt className="text-muted-foreground">Unmatched rule</dt>
                    <dd className="font-mono text-xs">
                      {result.unmatchedRule.attribute} {result.unmatchedRule.operator === "EQUALS" ? "=" : "!="} &quot;
                      {result.unmatchedRule.value}&quot;
                    </dd>
                  </>
                )}

                <dt className="text-muted-foreground">Environment</dt>
                <dd>{result.environmentName}</dd>
              </dl>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}
