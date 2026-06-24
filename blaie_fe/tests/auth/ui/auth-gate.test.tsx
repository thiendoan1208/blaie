import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthGate } from "@/features/auth/ui/auth-gate";
import { useCurrentUserQuery } from "@/features/auth/model/auth.queries";

vi.mock("@/features/auth/model/auth.queries", () => ({
  useCurrentUserQuery: vi.fn(),
}));

describe("AuthGate", () => {
  beforeEach(() => {
    vi.mocked(useCurrentUserQuery).mockReset();
  });

  it("shows a loading state while the current user is pending", () => {
    vi.mocked(useCurrentUserQuery).mockReturnValue({ isPending: true, isError: false } as never);

    render(<AuthGate>Private app</AuthGate>);

    expect(screen.getByText("Loading...")).toBeInTheDocument();
    expect(screen.queryByText("Private app")).not.toBeInTheDocument();
  });

  it("renders children when the current user is available", () => {
    vi.mocked(useCurrentUserQuery).mockReturnValue({ isPending: false, isError: false } as never);

    render(<AuthGate>Private app</AuthGate>);

    expect(screen.getByText("Private app")).toBeInTheDocument();
  });
});
