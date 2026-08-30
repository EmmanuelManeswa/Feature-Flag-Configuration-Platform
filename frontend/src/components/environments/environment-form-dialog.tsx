"use client";

import { useForm } from "@tanstack/react-form";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { FormFieldWrapper } from "@/components/form-field";
import { useCreateEnvironment } from "@/features/environments/hooks";
import { environmentFields } from "@/features/environments/schemas";
import { ApiError } from "@/lib/api-client";

export function EnvironmentFormDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const createEnvironment = useCreateEnvironment();

  const form = useForm({
    defaultValues: { name: "", description: "" },
    onSubmit: async ({ value }) => {
      try {
        await createEnvironment.mutateAsync({ name: value.name, description: value.description || undefined });
        toast.success("Environment created", { description: value.name });
        form.reset();
        onOpenChange(false);
      } catch (error) {
        const message = error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
        toast.error("Create failed", { description: message });
      }
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Create environment</DialogTitle>
        </DialogHeader>
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
            void form.handleSubmit();
          }}
        >
          <form.Field name="name" validators={{ onBlur: environmentFields.name }}>
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
                  onChange={(e) => field.handleChange(e.target.value.toUpperCase())}
                  placeholder="STAGING"
                  className="font-mono"
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

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <form.Subscribe selector={(state) => state.canSubmit}>
              {(canSubmit) => (
                <Button type="submit" disabled={!canSubmit || createEnvironment.isPending}>
                  {createEnvironment.isPending ? "Creating..." : "Create"}
                </Button>
              )}
            </form.Subscribe>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
