import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { changeOwnPassword, createUser, disableUser, enableUser, listUsers } from "./api";

export const userKeys = {
  all: ["users"] as const,
  list: (page: number) => ["users", "list", page] as const,
};

export function useUsers(page: number) {
  return useQuery({
    queryKey: userKeys.list(page),
    queryFn: () => listUsers({ page }),
    placeholderData: keepPreviousData,
  });
}

export function useCreateUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.all });
    },
  });
}

export function useDisableUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: disableUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.all });
    },
  });
}

export function useEnableUser() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: enableUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.all });
    },
  });
}

export function useChangeOwnPassword() {
  return useMutation({
    mutationFn: changeOwnPassword,
  });
}
