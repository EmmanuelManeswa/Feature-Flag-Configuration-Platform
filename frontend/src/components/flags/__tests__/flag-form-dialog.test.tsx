import { describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithQueryClient } from "@/test/render";
import { FlagFormDialog } from "@/components/flags/flag-form-dialog";
import * as flagsApi from "@/features/flags/api";
import * as environmentsApi from "@/features/environments/api";
import { ApiError } from "@/lib/api-client";
import type { FeatureFlagDto } from "@/types/api";

vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const FLAG: FeatureFlagDto = {
  id: "flag-1",
  key: "my-flag",
  name: "My Flag",
  description: null,
  environmentId: "env-1",
  environmentName: "DEV",
  type: "BOOLEAN",
  enabled: true,
  rolloutPercentage: null,
  targetingRules: [],
  version: 3,
  createdByEmail: "admin@example.com",
  updatedByEmail: "admin@example.com",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("FlagFormDialog", () => {
  it("shows a clear conflict message on a 409 and does not close the dialog", async () => {
    vi.spyOn(environmentsApi, "listEnvironments").mockResolvedValue([]);
    vi.spyOn(flagsApi, "updateFlag").mockRejectedValue(
      new ApiError(
        409,
        {
          type: "urn:problem-type:stale-version",
          title: "Stale version",
          status: 409,
          detail: "Feature flag flag-1 was modified by another user (expected version 3, current version 4)",
          instance: "/api/v1/flags/flag-1",
          correlationId: "test-id",
          currentVersion: 4,
          expectedVersion: 3,
        },
        "Stale version",
      ),
    );
    const onOpenChange = vi.fn();

    renderWithQueryClient(<FlagFormDialog open onOpenChange={onOpenChange} mode="edit" flag={FLAG} />);

    await userEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => expect(screen.getByText(/modified by another user/i)).toBeInTheDocument());
    // The dialog must stay open so the user doesn't lose their edits, and
    // onOpenChange(false) — which the success path calls — must not fire.
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
  });

  it("closes the dialog and does not show a conflict message on a successful update", async () => {
    vi.spyOn(environmentsApi, "listEnvironments").mockResolvedValue([]);
    vi.spyOn(flagsApi, "updateFlag").mockResolvedValue({ ...FLAG, version: 4, name: "My Flag" });
    const onOpenChange = vi.fn();

    renderWithQueryClient(<FlagFormDialog open onOpenChange={onOpenChange} mode="edit" flag={FLAG} />);

    await userEvent.click(screen.getByRole("button", { name: /save changes/i }));

    await waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    expect(screen.queryByText(/modified by another user/i)).not.toBeInTheDocument();
  });

  it("in create mode, submitting with an empty key/name/environment does not call the API", async () => {
    vi.spyOn(environmentsApi, "listEnvironments").mockResolvedValue([]);
    const createFlag = vi.spyOn(flagsApi, "createFlag");

    renderWithQueryClient(<FlagFormDialog open onOpenChange={vi.fn()} mode="create" />);
    await userEvent.click(screen.getByRole("button", { name: /create flag/i }));

    // Required-field validation (key/name/environment) must block the
    // request client-side — an empty submission should never reach the API.
    await waitFor(() => expect(screen.getAllByText(/required/i).length).toBeGreaterThan(0));
    expect(createFlag).not.toHaveBeenCalled();
  });
});
