"use client";

import { useState } from "react";
import { useForm } from "@tanstack/react-form";
import { Check, Copy, KeyRound } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { FormFieldWrapper } from "@/components/form-field";
import { useCreateUser } from "@/features/users/hooks";
import { createUserFields } from "@/features/users/schemas";
import { ApiError } from "@/lib/api-client";
import type { CreatedUserDto, Role } from "@/types/api";

export function CreateUserDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const createUser = useCreateUser();
  const [created, setCreated] = useState<CreatedUserDto | null>(null);
  const [copied, setCopied] = useState(false);

  const form = useForm({
    defaultValues: { email: "", displayName: "", role: "VIEWER" as Role },
    onSubmit: async ({ value }) => {
      try {
        const result = await createUser.mutateAsync(value);
        setCreated(result);
      } catch (error) {
        const message = error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
        toast.error("Create failed", { description: message });
      }
    },
  });

  function handleClose(nextOpen: boolean) {
    if (!nextOpen) {
      form.reset();
      setCreated(null);
      setCopied(false);
    }
    onOpenChange(nextOpen);
  }

  async function copyPassword() {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.generatedPassword);
      setCopied(true);
      toast.success("Password copied to clipboard");
    } catch {
      // Clipboard access can be denied (browser permission, a non-secure
      // context, an automated/headless environment) — without this catch,
      // the button would silently do nothing with no feedback at all. The
      // password text itself is select-all, so selecting and copying by
      // hand is always available as a fallback.
      toast.error("Couldn't copy automatically", { description: "Select the password above and copy it manually." });
    }
  }

  if (created) {
    return (
      <Dialog open={open} onOpenChange={handleClose}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <KeyRound className="size-4 text-primary" />
              Account created
            </DialogTitle>
            <DialogDescription>
              Copy this password now — it is generated once and cannot be retrieved again after you close this
              dialog. Share it with <strong>{created.user.displayName}</strong> out of band; they can change it
              themselves after logging in.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            <div className="rounded-md border border-border bg-muted/50 p-3">
              <p className="text-xs text-muted-foreground">{created.user.email}</p>
              <p className="mt-1 select-all break-all font-mono text-lg font-semibold tracking-wide">
                {created.generatedPassword}
              </p>
            </div>
            <Button type="button" variant="outline" className="w-full" onClick={copyPassword}>
              {copied ? <Check className="size-4" /> : <Copy className="size-4" />}
              {copied ? "Copied" : "Copy password"}
            </Button>
          </div>

          <DialogFooter>
            <Button type="button" onClick={() => handleClose(false)}>
              Done
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Create user</DialogTitle>
          <DialogDescription>A password is generated automatically — you&apos;ll copy it on the next step.</DialogDescription>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            void form.handleSubmit();
          }}
        >
          <form.Field name="email" validators={{ onBlur: createUserFields.email }}>
            {(field) => (
              <FormFieldWrapper
                label="Email"
                htmlFor={field.name}
                error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
              >
                <Input
                  id={field.name}
                  type="email"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(e) => field.handleChange(e.target.value)}
                  placeholder="person@example.com"
                />
              </FormFieldWrapper>
            )}
          </form.Field>

          <form.Field name="displayName" validators={{ onBlur: createUserFields.displayName }}>
            {(field) => (
              <FormFieldWrapper
                label="Display name"
                htmlFor={field.name}
                error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
              >
                <Input
                  id={field.name}
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(e) => field.handleChange(e.target.value)}
                  placeholder="Jane Doe"
                />
              </FormFieldWrapper>
            )}
          </form.Field>

          <form.Field name="role">
            {(field) => (
              <FormFieldWrapper label="Role" htmlFor={field.name}>
                <Select value={field.state.value} onValueChange={(v) => field.handleChange(v as Role)}>
                  <SelectTrigger id={field.name} className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="VIEWER">Viewer — read + evaluation playground</SelectItem>
                    <SelectItem value="ADMIN">Admin — full access</SelectItem>
                  </SelectContent>
                </Select>
              </FormFieldWrapper>
            )}
          </form.Field>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => handleClose(false)}>
              Cancel
            </Button>
            <form.Subscribe selector={(state) => state.canSubmit}>
              {(canSubmit) => (
                <Button type="submit" disabled={!canSubmit || createUser.isPending}>
                  {createUser.isPending ? "Creating..." : "Create"}
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
