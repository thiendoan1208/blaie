import { PagePlaceholder } from "@/shared/ui/page-placeholder";

export default function InformationPage() {
  return (
    <PagePlaceholder
      title="Information"
      description="Placeholder cho information."
      links={[{ href: "/", label: "Home" }]}
    />
  );
}
