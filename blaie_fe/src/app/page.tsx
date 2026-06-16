import { PagePlaceholder } from "@/shared/ui/page-placeholder";

export default function HomePage() {
  return (
    <PagePlaceholder
      title="Blaie skeleton"
      description="Temporary root page for the FE skeleton. There is no auth, no business flow, only routing and the minimal shared layer."
      links={[
        { href: "/login", label: "Login" },
        { href: "/inbox", label: "Inbox" },
        { href: "/admin", label: "Admin" },
      ]}
    />
  );
}
