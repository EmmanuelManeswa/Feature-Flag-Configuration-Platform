"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "@tanstack/react-form";
import { Flag, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { FormFieldWrapper } from "@/components/form-field";
import { useAuth } from "@/providers/auth-provider";
import { loginSchema } from "@/features/auth/schemas";
import { ApiError } from "@/lib/api-client";

export default function LoginPage() {
  const router = useRouter();
  const { user, isLoading, login } = useAuth();
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!isLoading && user) {
      router.replace("/");
    }
  }, [isLoading, user, router]);

  const form = useForm({
    defaultValues: { email: "", password: "" },
    onSubmit: async ({ value }) => {
      setIsSubmitting(true);
      try {
        await login(value.email, value.password);
        router.replace("/");
      } catch (error) {
        const message = error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
        toast.error("Sign in failed", { description: message });
      } finally {
        setIsSubmitting(false);
      }
    },
  });

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-b from-background to-muted/40 px-4">
      <div className="w-full max-w-sm space-y-6">
        <div className="flex flex-col items-center gap-2 text-center">
          <div className="flex size-11 items-center justify-center rounded-xl bg-primary text-primary-foreground shadow-sm">
            <Flag className="size-5" />
          </div>
          <h1 className="text-xl font-semibold tracking-tight">Feature Flag Platform</h1>
          <p className="text-sm text-muted-foreground">Sign in to manage flags, environments, and rollouts.</p>
        </div>

        <Card className="shadow-lg">
          <CardHeader>
            <CardTitle className="text-base">Sign in</CardTitle>
            <CardDescription>
              Demo accounts: <code className="font-mono text-xs">admin@example.com</code> /{" "}
              <code className="font-mono text-xs">viewer@example.com</code>, password{" "}
              <code className="font-mono text-xs">Password123!</code>
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form
              className="space-y-4"
              onSubmit={(e) => {
                e.preventDefault();
                e.stopPropagation();
                void form.handleSubmit();
              }}
            >
              <form.Field
                name="email"
                validators={{ onBlur: loginSchema.shape.email }}
              >
                {(field) => (
                  <FormFieldWrapper
                    label="Email"
                    htmlFor={field.name}
                    error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
                  >
                    <Input
                      id={field.name}
                      name={field.name}
                      type="email"
                      autoComplete="email"
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(e) => field.handleChange(e.target.value)}
                      disabled={isSubmitting}
                      autoFocus
                    />
                  </FormFieldWrapper>
                )}
              </form.Field>

              <form.Field
                name="password"
                validators={{ onBlur: loginSchema.shape.password }}
              >
                {(field) => (
                  <FormFieldWrapper
                    label="Password"
                    htmlFor={field.name}
                    error={field.state.meta.isTouched ? field.state.meta.errors[0]?.message : undefined}
                  >
                    <Input
                      id={field.name}
                      name={field.name}
                      type="password"
                      autoComplete="current-password"
                      value={field.state.value}
                      onBlur={field.handleBlur}
                      onChange={(e) => field.handleChange(e.target.value)}
                      disabled={isSubmitting}
                    />
                  </FormFieldWrapper>
                )}
              </form.Field>

              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? <Loader2 className="size-4 animate-spin" /> : null}
                Sign in
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
