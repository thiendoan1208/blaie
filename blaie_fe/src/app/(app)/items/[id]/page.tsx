import { PagePlaceholder } from "@/shared/ui/page-placeholder";
import { routePaths } from "@/shared/routes/route-paths";

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
      links={[{ href: routePaths.inbox, label: "Inbox" }, { href: routePaths.home, label: "Home" }]}
    />
  );
}
