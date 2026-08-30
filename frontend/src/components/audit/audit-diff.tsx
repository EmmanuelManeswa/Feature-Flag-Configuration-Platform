// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Json = any;

// Curated rather than exhaustive: the raw DTO snapshot has plenty of fields
// (id, createdAt, createdByEmail, ...) that never change between an
// UPDATE's before/after and would just be noise here.
const DIFF_FIELDS = ["name", "description", "enabled", "rolloutPercentage", "targetingRules"] as const;

function stringify(value: Json): string {
  if (value === null || value === undefined) return "—";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}

export function AuditDiff({
  action,
  previousValue,
  newValue,
}: {
  action: "CREATE" | "UPDATE" | "DELETE";
  previousValue: Json;
  newValue: Json;
}) {
  if (action === "CREATE") {
    return <p className="text-xs text-muted-foreground">Created with initial configuration.</p>;
  }
  if (action === "DELETE") {
    return <p className="text-xs text-muted-foreground">Flag deleted.</p>;
  }

  const changes = DIFF_FIELDS.map((field) => ({
    field,
    before: previousValue?.[field],
    after: newValue?.[field],
  })).filter(({ before, after }) => stringify(before) !== stringify(after));

  if (changes.length === 0) {
    return <p className="text-xs text-muted-foreground">Saved with no tracked field changes.</p>;
  }

  return (
    <dl className="space-y-1">
      {changes.map(({ field, before, after }) => (
        <div key={field} className="flex flex-wrap items-baseline gap-x-1.5 text-xs">
          <dt className="font-medium text-foreground">{field}:</dt>
          <dd className="font-mono text-destructive line-through decoration-destructive/60">{stringify(before)}</dd>
          <span className="text-muted-foreground">→</span>
          <dd className="font-mono text-success">{stringify(after)}</dd>
        </div>
      ))}
    </dl>
  );
}
