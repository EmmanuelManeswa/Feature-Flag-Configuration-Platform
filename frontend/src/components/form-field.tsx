import { Label } from "@/components/ui/label";
import type { ReactNode } from "react";

/**
 * Thin label+error wrapper used alongside TanStack Form's <form.Field>,
 * not a shadcn primitive (see components/ui/ — that's shadcn-only) and not
 * shadcn's own `Form` component (which is react-hook-form-specific; this
 * project uses TanStack Form throughout, per CLAUDE.md).
 */
export function FormFieldWrapper({
  label,
  htmlFor,
  error,
  description,
  children,
}: {
  label: string;
  htmlFor: string;
  error?: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {error ? (
        <p className="text-xs text-destructive">{error}</p>
      ) : description ? (
        <p className="text-xs text-muted-foreground">{description}</p>
      ) : null}
    </div>
  );
}
