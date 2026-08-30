import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createEnvironment, listEnvironments } from "./api";

export const environmentKeys = {
  all: ["environments"] as const,
};

export function useEnvironments() {
  return useQuery({
    queryKey: environmentKeys.all,
    queryFn: listEnvironments,
    staleTime: 5 * 60_000, // environments change rarely
  });
}

export function useCreateEnvironment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createEnvironment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: environmentKeys.all });
    },
  });
}
