import { NextResponse, type NextRequest } from "next/server";
import {
  authRoutePaths,
  defaultAuthenticatedRoute,
} from "@/shared/routes/route-paths";

const ACCESS_COOKIE_NAME = "blaie_at";
const AUTH_ROUTES = new Set<string>(authRoutePaths);

export function proxy(request: NextRequest) {
  const { nextUrl } = request;
  const { pathname } = nextUrl;
  const hasAccessToken = Boolean(request.cookies.get(ACCESS_COOKIE_NAME)?.value);

  if (AUTH_ROUTES.has(pathname) && hasAccessToken) {
    return NextResponse.redirect(new URL(defaultAuthenticatedRoute, nextUrl));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/((?!api|_next/static|_next/image|favicon.ico|sitemap.xml|robots.txt|.*\\..*).*)",
  ],
};
