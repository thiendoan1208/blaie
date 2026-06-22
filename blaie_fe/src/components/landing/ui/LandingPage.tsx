import { LandingHero } from "./LandingHero";
import { LandingNavigation } from "./LandingNavigation";

export function LandingPage() {
  return (
    <div className="landing-page font-body-md text-body-md antialiased select-none bg-ivory-canvas text-[#141413]">
      <LandingNavigation />
      <LandingHero />
    </div>
  );
}
