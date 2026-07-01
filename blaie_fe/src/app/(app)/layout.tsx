import type { ReactNode } from "react";

import { UserProvider } from "@/features/auth/model/user-context";
import { AuthGate } from "@/features/auth/ui/auth-gate";
import { AppShell } from "@/components/app/app-shell";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <UserProvider>
      <AuthGate>
        <AppShell>{children}</AppShell>
      </AuthGate>
    </UserProvider>
  );
}
