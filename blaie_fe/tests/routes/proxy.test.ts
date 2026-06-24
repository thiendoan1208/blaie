import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { proxy } from "@/proxy";

function request(pathname: string, cookie?: string) {
  return new NextRequest(new URL(pathname, "http://localhost:3000"), {
    headers: cookie ? { cookie } : undefined,
  });
}

describe("proxy", () => {
  it.each(["/login", "/register"])("redirects authenticated auth route %s to inbox", (pathname) => {
    const response = proxy(request(pathname, "blaie_at=access-token"));

    expect(response.headers.get("location")).toBe("http://localhost:3000/inbox");
  });

  it("allows anonymous auth routes", () => {
    const response = proxy(request("/login"));

    expect(response.headers.get("location")).toBeNull();
  });

  it("allows protected routes so AuthGate can attempt silent refresh", () => {
    const response = proxy(request("/inbox"));

    expect(response.headers.get("location")).toBeNull();
  });

  it("keeps the home page public", () => {
    const response = proxy(request("/"));

    expect(response.headers.get("location")).toBeNull();
  });
});
