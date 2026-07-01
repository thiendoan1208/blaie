import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AuthField } from "@/features/auth/ui/auth-field";

describe("AuthField", () => {
  it("associates the label with the input", () => {
    render(<AuthField id="email" label="Email" />);

    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });

  it("renders accessible hints and errors", () => {
    const { rerender } = render(<AuthField id="email" label="Email" hint="Use your work email." />);

    expect(screen.getByText("Use your work email.")).toHaveAttribute("id", "email-description");

    rerender(<AuthField id="email" label="Email" error="Email is invalid." />);

    const input = screen.getByLabelText("Email");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-describedby", "email-description");
    expect(screen.getByRole("alert")).toHaveTextContent("Email is invalid.");
    expect(screen.getByRole("alert")).toHaveAttribute("aria-live", "polite");
  });

  it("supports disabled inputs", () => {
    render(<AuthField id="email" label="Email" disabled />);

    expect(screen.getByLabelText("Email")).toBeDisabled();
  });
});
