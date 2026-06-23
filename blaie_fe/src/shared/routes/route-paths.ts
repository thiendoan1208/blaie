export const routePaths = {
  home: "/",
  login: "/login",
  register: "/register",
  inbox: "/inbox",
  tasks: "/tasks",
  notes: "/notes",
  calendar: "/calendar",
  reminders: "/reminders",
  information: "/information",
  search: "/search",
  admin: "/admin",
  itemDetail: (id: string) => `/items/${id}`,
  items: "/items",
};

export const defaultAuthenticatedRoute = routePaths.inbox;

export const authRoutePaths = [
  routePaths.login,
  routePaths.register,
] as const;

export const protectedRoutePrefixes = [
  routePaths.admin,
  routePaths.calendar,
  routePaths.inbox,
  routePaths.information,
  routePaths.items,
  routePaths.notes,
  routePaths.reminders,
  routePaths.search,
  routePaths.tasks,
] as const;

export const appNavigationItems = [
  { href: routePaths.inbox, label: "Inbox" },
  { href: routePaths.tasks, label: "Tasks" },
  { href: routePaths.notes, label: "Notes" },
  { href: routePaths.calendar, label: "Calendar" },
  { href: routePaths.reminders, label: "Reminders" },
  { href: routePaths.information, label: "Information" },
  { href: routePaths.search, label: "Search" },
  { href: routePaths.admin, label: "Admin" },
] as const;

export function isRoutePath(pathname: string, route: string) {
  return pathname === route || pathname.startsWith(`${route}/`);
}

export function isProtectedRoutePath(pathname: string) {
  return protectedRoutePrefixes.some((route) => isRoutePath(pathname, route));
}
