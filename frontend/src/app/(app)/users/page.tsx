"use client";

import { useState } from "react";
import { Plus, Users as UsersIcon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { DataTable } from "@/components/data-table";
import { getUserColumns } from "@/components/users/user-columns";
import { CreateUserDialog } from "@/components/users/create-user-dialog";
import { useDisableUser, useEnableUser, useUsers } from "@/features/users/hooks";
import { useAuth } from "@/providers/auth-provider";
import { ApiError } from "@/lib/api-client";
import type { UserDto } from "@/types/api";

export default function UsersPage() {
  const { user: currentUser } = useAuth();
  const [page, setPage] = useState(0);
  const usersQuery = useUsers(page);
  const disableUser = useDisableUser();
  const enableUser = useEnableUser();
  const [createOpen, setCreateOpen] = useState(false);
  const [pendingDisable, setPendingDisable] = useState<UserDto | null>(null);

  async function handleEnable(target: UserDto) {
    try {
      await enableUser.mutateAsync(target.id);
      toast.success("User enabled", { description: target.email });
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
      toast.error("Enable failed", { description: message });
    }
  }

  async function confirmDisable() {
    if (!pendingDisable) return;
    try {
      await disableUser.mutateAsync(pendingDisable.id);
      toast.success("User disabled", { description: pendingDisable.email });
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
      toast.error("Disable failed", { description: message });
    } finally {
      setPendingDisable(null);
    }
  }

  if (!currentUser) return null;

  if (currentUser.role !== "ADMIN") {
    // The sidebar link is already ADMIN-only, but the route itself is
    // reachable by URL — the backend rejects the underlying API calls with
    // a real 403 regardless (see UserApiTest.viewerCannotListOrCreateUsers),
    // this is just a clearer message than a silently-empty table.
    return (
      <div className="flex flex-col items-center gap-2 py-16 text-center">
        <UsersIcon className="size-8 text-muted-foreground" />
        <p className="text-sm font-medium">Admins only</p>
        <p className="text-sm text-muted-foreground">User management is restricted to the ADMIN role.</p>
      </div>
    );
  }

  const columns = getUserColumns({
    currentUserId: currentUser.id,
    pendingId: disableUser.isPending ? pendingDisable?.id : enableUser.isPending ? enableUser.variables : undefined,
    onDisable: setPendingDisable,
    onEnable: handleEnable,
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight">
            <UsersIcon className="size-6 text-muted-foreground" />
            Users
          </h1>
          <p className="text-sm text-muted-foreground">
            Create accounts with a backend-generated password, and disable access without deleting history.
          </p>
        </div>
        <Button onClick={() => setCreateOpen(true)}>
          <Plus className="size-4" />
          Create user
        </Button>
      </div>

      <DataTable
        columns={columns}
        data={usersQuery.data?.content ?? []}
        isLoading={usersQuery.isLoading}
        emptyMessage="No users yet."
        page={page}
        totalPages={usersQuery.data?.totalPages ?? 0}
        onPageChange={setPage}
      />

      <CreateUserDialog open={createOpen} onOpenChange={setCreateOpen} />

      <AlertDialog open={pendingDisable !== null} onOpenChange={(open) => !open && setPendingDisable(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Disable {pendingDisable?.displayName}?</AlertDialogTitle>
            <AlertDialogDescription>
              They will immediately lose API access — including any session already in progress, since the
              account is re-checked on every request, not just at login. This does not delete their account or
              history; an admin can re-enable it at any time.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={confirmDisable}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              Disable
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
