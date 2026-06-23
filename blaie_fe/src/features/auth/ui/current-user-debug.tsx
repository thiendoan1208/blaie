"use client";

import { isAppError } from "@/shared/api/errors/app-error";
import { useCurrentUserQuery } from "../model/auth.queries";

export function CurrentUserDebug() {
  const { data, error, isError, isPending, refetch } = useCurrentUserQuery();

  return (
    <aside className="fixed right-4 bottom-4 z-50 max-w-md rounded-lg border border-white/15 bg-black/80 p-4 font-mono text-xs text-white shadow-2xl backdrop-blur">
      <div className="mb-2 flex items-center justify-between gap-4">
        <strong>Auth debug</strong>
        <button
          type="button"
          onClick={() => void refetch()}
          className="rounded border border-white/20 px-2 py-1 text-[11px] hover:bg-white/10"
        >
          Refetch
        </button>
      </div>
      {isPending ? <p>Loading current user...</p> : null}
      {isError ? (
        <pre className="max-h-48 overflow-auto whitespace-pre-wrap">
          {JSON.stringify(
            isAppError(error)
              ? { code: error.code, status: error.status, message: error.message, requestId: error.requestId }
              : { message: "Unknown error" },
            null,
            2,
          )}
        </pre>
      ) : null}
      {data ? <pre className="max-h-48 overflow-auto whitespace-pre-wrap">{JSON.stringify(data, null, 2)}</pre> : null}
    </aside>
  );
}
