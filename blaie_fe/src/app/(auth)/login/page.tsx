import { PagePlaceholder } from "@/shared/ui/page-placeholder";

export default function LoginPage() {
  return (
    <PagePlaceholder
      title="Login"
      description="Placeholder for the login screen. The auth flow will be finalized after BE and FE agree on the contract."
      links={[{ href: "/register", label: "Go to register" }, { href: "/", label: "Home" }]}
    />
  );
}
