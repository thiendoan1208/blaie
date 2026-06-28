"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { Toaster } from "@/components/ui/sonner";
import { ensureCsrfToken } from "@/features/auth/api/csrf";
import { createQueryClient } from "@/shared/query/query-client";
import { TooltipProvider } from "@/components/ui/tooltip";

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(() => createQueryClient());

  useEffect(() => {
    void ensureCsrfToken().catch(() => {
      // Unsafe requests will surface the CSRF problem if bootstrap cannot complete.
    });
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>{children}</TooltipProvider>
      <Toaster position="top-right" richColors />
    </QueryClientProvider>
  );
}
