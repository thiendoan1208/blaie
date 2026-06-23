import { LandingPage } from "@/components/landing/ui/LandingPage";
import { CurrentUserDebug } from "@/features/auth/ui/current-user-debug";

export default function HomePage() {
  return (
    <>
      <LandingPage />
      <CurrentUserDebug />
    </>
  );
}
