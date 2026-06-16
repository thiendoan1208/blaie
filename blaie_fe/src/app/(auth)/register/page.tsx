import { PagePlaceholder } from "@/shared/ui/page-placeholder";

export default function RegisterPage() {
  return (
    <PagePlaceholder
      title="Register"
      description="Placeholder for the registration screen. There is no real auth schema or user database model yet."
      links={[{ href: "/login", label: "Go to login" }, { href: "/", label: "Home" }]}
    />
  );
}
