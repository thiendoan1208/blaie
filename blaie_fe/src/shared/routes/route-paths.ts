import {
  Bell,
  Calendar,
  CheckSquare,
  Inbox,
  Info,
  Layers,
  Search,
  type LucideIcon,
} from "lucide-react";

export const routePaths = {
  home: "/",
  login: "/login",
  register: "/register",
  forgotPassword: "/forgot-password",
  verifyEmail: "/verify-email",
  verifyEmailResult: "/verify-email/result",
  inbox: "/inbox",
  tasks: "/tasks",
  calendar: "/calendar",
  reminders: "/reminders",
  information: "/information",
  search: "/search",
  admin: "/admin",
  itemDetail: (id: string) => `/items/${id}`,
  items: "/items",
};

export type AppNavigationItem = {
  label: string;
  href: string;
  icon: LucideIcon;
};

export const appNavigationItems: AppNavigationItem[] = [
  { label: "Inbox", href: routePaths.inbox, icon: Inbox },
  { label: "Tasks", href: routePaths.tasks, icon: CheckSquare },
  { label: "Calendar", href: routePaths.calendar, icon: Calendar },
  { label: "Reminders", href: routePaths.reminders, icon: Bell },
  { label: "Information", href: routePaths.information, icon: Info },
];

export const defaultAuthenticatedRoute = routePaths.inbox;

export const authRoutePaths = [
  routePaths.login,
  routePaths.register,
  routePaths.forgotPassword,
] as const;

export const protectedRoutePrefixes = [
  routePaths.admin,
  routePaths.calendar,
  routePaths.inbox,
  routePaths.information,
  routePaths.items,
  routePaths.reminders,
  routePaths.search,
  routePaths.tasks,
] as const;

export function isRoutePath(pathname: string, route: string) {
  return pathname === route || pathname.startsWith(`${route}/`);
}

export function isProtectedRoutePath(pathname: string) {
  return protectedRoutePrefixes.some((route) => isRoutePath(pathname, route));
}
