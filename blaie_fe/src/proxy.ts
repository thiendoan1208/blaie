import { NextResponse, type NextRequest } from "next/server";
import {
  authRoutePaths,
  defaultAuthenticatedRoute,
  isProtectedRoutePath,
  routePaths,
} from "@/shared/routes/route-paths";

const ACCESS_COOKIE_NAME = "blaie_at";
const AUTH_ROUTES = new Set<string>(authRoutePaths);

export function proxy(request: NextRequest) {
  const { nextUrl } = request;
  const { pathname, search } = nextUrl;
  const hasAccessToken = Boolean(request.cookies.get(ACCESS_COOKIE_NAME)?.value);

  if (AUTH_ROUTES.has(pathname) && hasAccessToken) {
    return NextResponse.redirect(new URL(defaultAuthenticatedRoute, nextUrl));
  }

  if (isProtectedRoutePath(pathname) && !hasAccessToken) {
    const loginUrl = new URL(routePaths.login, nextUrl);
    loginUrl.searchParams.set("next", `${pathname}${search}`);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/((?!api|_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt|.*\\..*).*)",
  ],
};
