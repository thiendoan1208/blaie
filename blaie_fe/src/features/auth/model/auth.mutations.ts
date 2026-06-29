import { useMutation } from "@tanstack/react-query";
import { useQueryClient } from "@tanstack/react-query";
import {
  confirmPasswordReset,
  login,
  logout,
  register,
  requestPasswordReset,
  resendEmailVerification,
  updatePassword,
  updateUsername,
} from "../api/auth.service";
import { authKeys } from "./auth.keys";

export function useLoginMutation() {
  return useMutation({
    mutationFn: login,
  });
}

export function useRegisterMutation() {
  return useMutation({
    mutationFn: register,
  });
}

export function useResendEmailVerificationMutation() {
  return useMutation({
    mutationFn: resendEmailVerification,
  });
}

export function useRequestPasswordResetMutation() {
  return useMutation({
    mutationFn: requestPasswordReset,
  });
}

export function useConfirmPasswordResetMutation() {
  return useMutation({
    mutationFn: confirmPasswordReset,
  });
}

export function useLogoutMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: authKeys.currentUser() });
    },
  });
}

export function useUpdateUsernameMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updateUsername,
    onSuccess: (user) => {
      queryClient.setQueryData(authKeys.currentUser(), user);
    },
  });
}

export function useUpdatePasswordMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: updatePassword,
    onSuccess: (user) => {
      queryClient.setQueryData(authKeys.currentUser(), user);
    },
  });
}
