import { describe, expect, it, vi, beforeEach } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQueryClient } from "@/test/render";
import { CreateUserDialog } from "@/components/users/create-user-dialog";
import * as usersApi from "@/features/users/api";

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

beforeEach(() => {
  Object.assign(navigator, { clipboard: { writeText: vi.fn().mockResolvedValue(undefined) } });
});

describe("CreateUserDialog", () => {
  it("blocks submission with empty required fields and never calls the API", async () => {
    const createUser = vi.spyOn(usersApi, "createUser");

    renderWithQueryClient(<CreateUserDialog open onOpenChange={vi.fn()} />);
    await userEvent.click(screen.getByRole("button", { name: /^create$/i }));

    await waitFor(() => expect(screen.getAllByText(/required/i).length).toBeGreaterThan(0));
    expect(createUser).not.toHaveBeenCalled();
  });

  it("shows the one-time generated password after a successful create, not an immediate close", async () => {
    vi.spyOn(usersApi, "createUser").mockResolvedValue({
      user: { id: "u1", email: "new@example.com", displayName: "New Person", role: "VIEWER", enabled: true, createdAt: "2026-01-01T00:00:00Z" },
      generatedPassword: "aB3$xY9!kLm2Qp7R",
    });
    const onOpenChange = vi.fn();

    renderWithQueryClient(<CreateUserDialog open onOpenChange={onOpenChange} />);
    await userEvent.type(screen.getByLabelText(/email/i), "new@example.com");
    await userEvent.type(screen.getByLabelText(/display name/i), "New Person");
    await userEvent.click(screen.getByRole("button", { name: /^create$/i }));

    // The dialog must not close itself on success — the admin has to
    // explicitly acknowledge (Done) after copying the one-time password.
    await waitFor(() => expect(screen.getByText("aB3$xY9!kLm2Qp7R")).toBeInTheDocument());
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it("copies the generated password to the clipboard", async () => {
    vi.spyOn(usersApi, "createUser").mockResolvedValue({
      user: { id: "u1", email: "new@example.com", displayName: "New Person", role: "VIEWER", enabled: true, createdAt: "2026-01-01T00:00:00Z" },
      generatedPassword: "aB3$xY9!kLm2Qp7R",
    });

    renderWithQueryClient(<CreateUserDialog open onOpenChange={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/email/i), "new@example.com");
    await userEvent.type(screen.getByLabelText(/display name/i), "New Person");
    await userEvent.click(screen.getByRole("button", { name: /^create$/i }));
    await waitFor(() => expect(screen.getByText("aB3$xY9!kLm2Qp7R")).toBeInTheDocument());

    await userEvent.click(screen.getByRole("button", { name: /copy password/i }));

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith("aB3$xY9!kLm2Qp7R");
    await waitFor(() => expect(screen.getByRole("button", { name: /^copied$/i })).toBeInTheDocument());
  });

  it("tells the admin to copy manually when clipboard access is denied, instead of failing silently", async () => {
    Object.assign(navigator, { clipboard: { writeText: vi.fn().mockRejectedValue(new Error("permission denied")) } });
    vi.spyOn(usersApi, "createUser").mockResolvedValue({
      user: { id: "u1", email: "new@example.com", displayName: "New Person", role: "VIEWER", enabled: true, createdAt: "2026-01-01T00:00:00Z" },
      generatedPassword: "aB3$xY9!kLm2Qp7R",
    });
    const { toast } = await import("sonner");

    renderWithQueryClient(<CreateUserDialog open onOpenChange={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/email/i), "new@example.com");
    await userEvent.type(screen.getByLabelText(/display name/i), "New Person");
    await userEvent.click(screen.getByRole("button", { name: /^create$/i }));
    await waitFor(() => expect(screen.getByText("aB3$xY9!kLm2Qp7R")).toBeInTheDocument());

    await userEvent.click(screen.getByRole("button", { name: /copy password/i }));

    // Must not silently do nothing — this button label staying "Copy
    // password" instead of flipping to "Copied" is exactly the bug this
    // guards against, so assert the failure path is visible to the admin.
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: /^copied$/i })).not.toBeInTheDocument();
  });
});
