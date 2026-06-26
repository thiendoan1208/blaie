import { useMutation } from "@tanstack/react-query";
import { login, logout, register, resendEmailVerification } from "../api/auth.service";

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

export function useLogoutMutation() {
  return useMutation({
    mutationFn: logout,
  });
}
