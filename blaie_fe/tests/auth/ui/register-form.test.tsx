import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createAppError } from "@/shared/api/errors/app-error";
import { RegisterForm } from "@/features/auth/ui/register-form";
import { useRegisterMutation } from "@/features/auth/model/auth.mutations";

vi.mock("@/features/auth/model/auth.mutations", () => ({
  useRegisterMutation: vi.fn(),
}));

const router = {
  push: vi.fn(),
  refresh: vi.fn(),
};

vi.mock("next/navigation", () => ({
  useRouter: () => router,
}));

describe("RegisterForm", () => {
  beforeEach(() => {
    vi.mocked(useRegisterMutation).mockReturnValue({
      isPending: false,
      mutateAsync: vi.fn(),
    } as never);
    router.push.mockReset();
    router.refresh.mockReset();
  });

  it("disables all fields and shows a spinner while submitting", () => {
    vi.mocked(useRegisterMutation).mockReturnValue({
      isPending: true,
      mutateAsync: vi.fn(),
    } as never);

    render(<RegisterForm />);

    expect(screen.getByLabelText("Display name")).toBeDisabled();
    expect(screen.getByLabelText("Username")).toBeDisabled();
    expect(screen.getByLabelText("Email")).toBeDisabled();
    expect(screen.getByLabelText("Password")).toBeDisabled();
    expect(screen.getByRole("button", { name: "Show password" })).toBeDisabled();
    expect(screen.getByRole("button", { name: /Creating account/i })).toBeDisabled();
    expect(screen.getByRole("link", { name: /Continue with Google/i })).toHaveAttribute("aria-disabled", "true");
  });

  it("renders the Google OAuth start link", () => {
    render(<RegisterForm />);

    expect(screen.getByRole("link", { name: /Continue with Google/i })).toHaveAttribute(
      "href",
      "http://localhost:8080/api/v1/auth/google/start?next=%2Finbox",
    );
  });

  it("submits valid input and redirects to inbox", async () => {
    const mutateAsync = vi.fn().mockResolvedValue({ id: "user-1", emailVerified: true });
    vi.mocked(useRegisterMutation).mockReturnValue({
      isPending: false,
      mutateAsync,
    } as never);

    render(<RegisterForm />);
    fireEvent.change(screen.getByLabelText("Display name"), { target: { value: "Blaie User" } });
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "blaie.user" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "user@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "Password1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));

    await waitFor(() => expect(mutateAsync).toHaveBeenCalledWith({
      displayName: "Blaie User",
      username: "blaie.user",
      email: "user@example.com",
      password: "Password1!",
    }));
    expect(router.push).toHaveBeenCalledWith("/inbox");
    expect(router.refresh).toHaveBeenCalled();
  });

  it("maps duplicate username errors to the username field", async () => {
    const mutateAsync = vi.fn().mockRejectedValue(
      createAppError({ code: "USERNAME_ALREADY_EXISTS", status: 409, message: "Duplicate" }),
    );
    vi.mocked(useRegisterMutation).mockReturnValue({
      isPending: false,
      mutateAsync,
    } as never);

    render(<RegisterForm />);
    fireEvent.change(screen.getByLabelText("Display name"), { target: { value: "Blaie User" } });
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "blaie.user" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "user@example.com" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "Password1!" } });
    fireEvent.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Username already exists.");
  });
});
