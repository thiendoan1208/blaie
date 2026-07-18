import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { logout } from "@/features/auth/api/auth.service";
import { useLogoutMutation } from "@/features/auth/model/auth.mutations";
import { clearAllInboxTracking } from "@/features/inbox/model/inbox-tracking";

vi.mock("@/features/auth/api/auth.service", () => ({
  confirmPasswordReset: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  requestPasswordReset: vi.fn(),
  resendEmailVerification: vi.fn(),
  updatePassword: vi.fn(),
  updateUsername: vi.fn(),
}));

vi.mock("@/features/inbox/model/inbox-tracking", () => ({
  clearAllInboxTracking: vi.fn(),
}));

function wrapper(queryClient: QueryClient) {
  return function QueryWrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    );
  };
}

describe("Auth mutations", () => {
  beforeEach(() => {
    vi.mocked(logout).mockReset();
    vi.mocked(clearAllInboxTracking).mockReset();
  });

  it("clears private browser and query state even when server logout fails", async () => {
    vi.mocked(logout).mockRejectedValue(new Error("network unavailable"));
    const queryClient = new QueryClient({
      defaultOptions: { mutations: { retry: false } },
    });
    queryClient.setQueryData(["inbox", "user-1"], { originalText: "private text" });
    const { result } = renderHook(() => useLogoutMutation(), {
      wrapper: wrapper(queryClient),
    });

    await act(async () => {
      await expect(result.current.mutateAsync()).rejects.toThrow("network unavailable");
    });

    await waitFor(() => expect(clearAllInboxTracking).toHaveBeenCalledOnce());
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
  });
});
