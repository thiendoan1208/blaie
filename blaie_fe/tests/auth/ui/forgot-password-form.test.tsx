import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ForgotPasswordForm } from "@/features/auth/ui/forgot-password-form";
import {
  useConfirmPasswordResetMutation,
  useRequestPasswordResetMutation,
} from "@/features/auth/model/auth.mutations";
import { createAppError } from "@/shared/api/errors/app-error";
import { toast } from "sonner";

vi.mock("@/features/auth/model/auth.mutations", () => ({
  useRequestPasswordResetMutation: vi.fn(),
  useConfirmPasswordResetMutation: vi.fn(),
}));

const router = {
  push: vi.fn(),
  refresh: vi.fn(),
};

vi.mock("next/navigation", () => ({
  useRouter: () => router,
}));

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

describe("ForgotPasswordForm", () => {
  beforeEach(() => {
    vi.mocked(useRequestPasswordResetMutation).mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn().mockResolvedValue(undefined),
    } as never);
    vi.mocked(useConfirmPasswordResetMutation).mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn().mockResolvedValue(undefined),
    } as never);
    router.push.mockReset();
    router.refresh.mockReset();
    vi.mocked(toast.success).mockReset();
    vi.mocked(toast.error).mockReset();
  });

  it("requests a reset code and advances to the code step", async () => {
    const requestReset = vi.fn().mockResolvedValue(undefined);
    vi.mocked(useRequestPasswordResetMutation).mockReturnValue({
      isPending: false,
      mutateAsync: requestReset,
    } as never);

    render(<ForgotPasswordForm />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: " user@example.com " } });
    fireEvent.click(screen.getByRole("button", { name: "Send reset code" }));

    await waitFor(() => expect(requestReset).toHaveBeenCalledWith({ email: "user@example.com" }));
    expect(await screen.findByRole("heading", { name: "Enter your code." })).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveValue("user@example.com");
    expect(toast.success).toHaveBeenCalledWith("If that email belongs to an account, we sent a reset code.");
  });

  it("confirms a reset code and redirects to login", async () => {
    const confirmReset = vi.fn().mockResolvedValue(undefined);
    vi.mocked(useConfirmPasswordResetMutation).mockReturnValue({
      isPending: false,
      mutateAsync: confirmReset,
    } as never);

    render(<ForgotPasswordForm />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "user@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Send reset code" }));
    await screen.findByRole("heading", { name: "Enter your code." });

    fireEvent.change(screen.getByLabelText("Reset code"), { target: { value: "123456" } });
    fireEvent.change(screen.getByLabelText("New password"), { target: { value: "Password2@" } });
    fireEvent.change(screen.getByLabelText("Confirm password"), { target: { value: "Password2@" } });
    fireEvent.click(screen.getByRole("button", { name: "Reset password" }));

    await waitFor(() => expect(confirmReset).toHaveBeenCalledWith({
      email: "user@example.com",
      code: "123456",
      newPassword: "Password2@",
    }));
    expect(router.push).toHaveBeenCalledWith("/login");
    expect(router.refresh).toHaveBeenCalled();
  });

  it("maps invalid reset code errors to the code field", async () => {
    vi.mocked(useConfirmPasswordResetMutation).mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn().mockRejectedValue(
        createAppError({
          code: "PASSWORD_RESET_INVALID_CODE",
          status: 400,
          message: "Invalid password reset code",
        }),
      ),
    } as never);

    render(<ForgotPasswordForm />);
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "user@example.com" } });
    fireEvent.click(screen.getByRole("button", { name: "Send reset code" }));
    await screen.findByRole("heading", { name: "Enter your code." });

    fireEvent.change(screen.getByLabelText("Reset code"), { target: { value: "123456" } });
    fireEvent.change(screen.getByLabelText("New password"), { target: { value: "Password2@" } });
    fireEvent.change(screen.getByLabelText("Confirm password"), { target: { value: "Password2@" } });
    fireEvent.click(screen.getByRole("button", { name: "Reset password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The reset code is incorrect.");
  });
});
