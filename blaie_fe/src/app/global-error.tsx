"use client";

import { useEffect } from "react";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Global error boundary captured", {
      message: error.message,
      digest: error.digest,
      stack: error.stack,
    });
  }, [error]);

  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen bg-background text-foreground antialiased">
        <main className="flex min-h-screen items-center justify-center p-6">
          <section className="max-w-lg rounded-2xl border border-border bg-card p-6 text-card-foreground shadow-sm">
            <h1 className="text-lg font-semibold">Application error</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              An unexpected application error happened. {error.digest ? `Digest: ${error.digest}` : null}
            </p>
            <button
              className="mt-4 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
              onClick={reset}
              type="button"
            >
              Reload
            </button>
          </section>
        </main>
      </body>
    </html>
  );
}
