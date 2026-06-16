import { PagePlaceholder } from "@/shared/ui/page-placeholder";

export default async function ItemDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  return (
    <PagePlaceholder
      title={`Item ${id}`}
      description="Placeholder for the item detail page."
      links={[{ href: "/inbox", label: "Inbox" }, { href: "/", label: "Home" }]}
    />
  );
}
