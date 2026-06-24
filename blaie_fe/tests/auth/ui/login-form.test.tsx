import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createAppError } from "@/shared/api/errors/app-error";
import { LoginForm } from "@/features/auth/ui/login-form";
import { useLoginMutation } from "@/features/auth/model/auth.mutations";
import { toast } from "sonner";

vi.mock("@/features/auth/model/auth.mutations", () => ({
  useLoginMutation: vi.fn(),
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
    error: vi.fn(),
  },
}));

describe("LoginForm", () => {
  beforeEach(() => {
    vi.mocked(useLoginMutation).mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn(),
    } as never);
    router.push.mockReset();
    router.refresh.mockReset();
    vi.mocked(toast.error).mockReset();
  });

  it("disables fields and shows a spinner while submitting", () => {
    vi.mocked(useLoginMutation).mockReturnValue({
      isPending: true,
      mutateAsync: vi.fn(),
    } as never);

    render(<LoginForm />);

    expect(screen.getByLabelText("Username or email")).toBeDisabled();
    expect(screen.getByLabelText("Password")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Show password" })).toBeDisabled();
    expect(screen.getByRole("button", { name: /Signing in/i })).toBeDisabled();
  });

  it("submits valid input and redirects to inbox", async () => {
    const mutateAsync = vi.fn().mockResolvedValue({ id: "user-1" });
    vi.mocked(useLoginMutation).mockReturnValue({
      isPending: false,
      mutateAsync,
    } as never);

    render(<LoginForm />);
    fireEvent.change(screen.getByLabelText("Username or email"), { target: { value: "user@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "Password1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledWith({
      identifier: "user@example.com",
      password: "Password1!",
    }));
    expect(router.push).toHaveBeenCalledWith("/inbox");
    expect(router.refresh).toHaveBeenCalled();
  });

  it("renders and toasts root server errors", async () => {
    const mutateAsync = vi.fn().mockRejectedValue(
      createAppError({ code: "INVALID_CREDENTIALS", status: 401, message: "Invalid" }),
    );
    vi.mocked(useLoginMutation).mockReturnValue({
      isPending: false,
      mutateAsync,
    } as never);

    render(<LoginForm />);
    fireEvent.change(screen.getByLabelText("Username or email"), { target: { value: "user@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "Password1!" } });
    fireEvent.submit(screen.getByRole("button", { name: "Sign in" }).closest("form")!);

    await waitFor(() => expect(mutateAsync).toHaveBeenCalled());
    expect(await screen.findByRole("alert")).toHaveTextContent("Username, email, or password is incorrect.");
    expect(toast.error).toHaveBeenCalledWith("Username, email, or password is incorrect.");
  });
});
