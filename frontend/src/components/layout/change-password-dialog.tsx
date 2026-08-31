"use client";

import { useForm } from "@tanstack/react-form";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { FormFieldWrapper } from "@/components/form-field";
import { useChangeOwnPassword } from "@/features/users/hooks";
import { changePasswordFields } from "@/features/users/schemas";
import { ApiError } from "@/lib/api-client";

export function ChangePasswordDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const changePassword = useChangeOwnPassword();

  const form = useForm({
    defaultValues: { currentPassword: "", newPassword: "" },
    onSubmit: async ({ value }) => {
      try {
        await changePassword.mutateAsync(value);
        toast.success("Password changed");
        form.reset();
        onOpenChange(false);
      } catch (error) {
        const message = error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
        toast.error("Change password failed", { description: message });
      }
    },
  });

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) form.reset();
        onOpenChange(next);
      }}
    >
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Change password</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            void form.handleSubmit();
          }}
        >
          <form.Field name="currentPassword" validators={{ onBlur: changePasswordFields.currentPassword }}>
            {(field) => (
              <FormFieldWrapper
                label="Current password"
                htmlFor={field.name}
                error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
              >
                <Input
                  id={field.name}
                  type="password"
                  autoComplete="current-password"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(e) => field.handleChange(e.target.value)}
                />
              </FormFieldWrapper>
            )}
          </form.Field>

          <form.Field name="newPassword" validators={{ onBlur: changePasswordFields.newPassword }}>
            {(field) => (
              <FormFieldWrapper
                label="New password"
                htmlFor={field.name}
                error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
              >
                <Input
                  id={field.name}
                  type="password"
                  autoComplete="new-password"
                  value={field.state.value}
                  onBlur={field.handleBlur}
                  onChange={(e) => field.handleChange(e.target.value)}
                />
              </FormFieldWrapper>
            )}
          </form.Field>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <form.Subscribe selector={(state) => state.canSubmit}>
              {(canSubmit) => (
                <Button type="submit" disabled={!canSubmit || changePassword.isPending}>
                  {changePassword.isPending ? "Changing..." : "Change password"}
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
