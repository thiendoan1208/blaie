"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import type { TextCapture } from "../types/inbox";

const STORAGE_PREFIX = "blaie.inbox.capture-tracking.v1";
const STATE_VERSION = 1;
const MAX_TRACKED_CAPTURES = 100;
const IDEMPOTENCY_TTL_MS = 24 * 60 * 60 * 1_000;
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export type PendingCaptureSubmission = {
  textHash: string;
  idempotencyKey: string;
  createdAt: string;
  captureId: string | null;
};

export type InboxTrackingState = {
  version: 1;
  captureIds: string[];
  pendingSubmissions: PendingCaptureSubmission[];
};

function emptyState(): InboxTrackingState {
  return { version: STATE_VERSION, captureIds: [], pendingSubmissions: [] };
}

function storageKey(userId: string): string {
  return `${STORAGE_PREFIX}:${userId}`;
}

function browserStorage(): Storage | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function isPendingSubmission(value: unknown): value is PendingCaptureSubmission {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.textHash === "string" &&
    /^[a-f0-9]{64}$/.test(candidate.textHash) &&
    typeof candidate.idempotencyKey === "string" &&
    UUID_PATTERN.test(candidate.idempotencyKey) &&
    typeof candidate.createdAt === "string" &&
    !Number.isNaN(Date.parse(candidate.createdAt)) &&
    (candidate.captureId === null ||
      (typeof candidate.captureId === "string" &&
        UUID_PATTERN.test(candidate.captureId)))
  );
}

function compactState(
  state: InboxTrackingState,
  now = Date.now(),
): InboxTrackingState {
  const captureIds = [
    ...new Set(state.captureIds.filter((captureId) => UUID_PATTERN.test(captureId))),
  ].slice(-MAX_TRACKED_CAPTURES);
  const pendingSubmissions = state.pendingSubmissions
    .filter(
      (submission) =>
        now - Date.parse(submission.createdAt) < IDEMPOTENCY_TTL_MS,
    )
    .slice(-MAX_TRACKED_CAPTURES);
  return { version: STATE_VERSION, captureIds, pendingSubmissions };
}

export function readInboxTrackingState(
  userId: string,
  now = Date.now(),
): InboxTrackingState {
  const storage = browserStorage();
  if (!storage) return emptyState();

  try {
    const raw = storage.getItem(storageKey(userId));
    if (!raw) return emptyState();
    const parsed = JSON.parse(raw) as Partial<InboxTrackingState>;
    if (
      parsed.version !== STATE_VERSION ||
      !Array.isArray(parsed.captureIds) ||
      !Array.isArray(parsed.pendingSubmissions)
    ) {
      return emptyState();
    }

    return compactState(
      {
        version: STATE_VERSION,
        captureIds: parsed.captureIds.filter(
          (captureId): captureId is string => typeof captureId === "string",
        ),
        pendingSubmissions: parsed.pendingSubmissions.filter(
          isPendingSubmission,
        ),
      },
      now,
    );
  } catch {
    return emptyState();
  }
}

function writeInboxTrackingState(
  userId: string,
  state: InboxTrackingState,
  now = Date.now(),
): InboxTrackingState {
  const compacted = compactState(state, now);
  const storage = browserStorage();
  if (!storage) return compacted;

  try {
    if (
      compacted.captureIds.length === 0 &&
      compacted.pendingSubmissions.length === 0
    ) {
      storage.removeItem(storageKey(userId));
    } else {
      storage.setItem(storageKey(userId), JSON.stringify(compacted));
    }
  } catch {
    // Tracking improves recovery but must never block capture submission.
  }
  return compacted;
}

export function normalizeCaptureText(text: string): string {
  return text.trim();
}

export async function hashCaptureText(text: string): Promise<string> {
  const bytes = new TextEncoder().encode(normalizeCaptureText(text));
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)]
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("");
}

export async function getOrCreatePendingSubmission(
  userId: string,
  text: string,
  now = Date.now(),
): Promise<PendingCaptureSubmission> {
  const textHash = await hashCaptureText(text);
  const state = readInboxTrackingState(userId, now);
  const existing = state.pendingSubmissions.find(
    (submission) => submission.textHash === textHash,
  );
  if (existing) return existing;

  const submission: PendingCaptureSubmission = {
    textHash,
    idempotencyKey: crypto.randomUUID(),
    createdAt: new Date(now).toISOString(),
    captureId: null,
  };
  writeInboxTrackingState(
    userId,
    {
      ...state,
      pendingSubmissions: [...state.pendingSubmissions, submission],
    },
    now,
  );
  return submission;
}

function addCaptureIds(
  userId: string,
  captureIds: string[],
  now = Date.now(),
): InboxTrackingState {
  const state = readInboxTrackingState(userId, now);
  return writeInboxTrackingState(
    userId,
    { ...state, captureIds: [...state.captureIds, ...captureIds] },
    now,
  );
}

function attachCapture(
  userId: string,
  submission: PendingCaptureSubmission,
  capture: TextCapture,
  now = Date.now(),
): InboxTrackingState {
  const state = readInboxTrackingState(userId, now);
  const terminal = capture.processingStatus !== "processing";
  const pendingSubmissions = terminal
    ? state.pendingSubmissions.filter(
        (candidate) =>
          candidate.idempotencyKey !== submission.idempotencyKey,
      )
    : state.pendingSubmissions.map((candidate) =>
        candidate.idempotencyKey === submission.idempotencyKey
          ? { ...candidate, captureId: capture.id }
          : candidate,
      );
  return writeInboxTrackingState(
    userId,
    {
      ...state,
      captureIds: [...state.captureIds, capture.id],
      pendingSubmissions,
    },
    now,
  );
}

async function reconcileRecoveredCapture(
  userId: string,
  capture: TextCapture,
  now = Date.now(),
): Promise<InboxTrackingState> {
  const textHash = await hashCaptureText(capture.originalText);
  const state = readInboxTrackingState(userId, now);
  const terminal = capture.processingStatus !== "processing";
  const pendingSubmissions = state.pendingSubmissions.flatMap((submission) => {
    const belongsToCapture =
      submission.captureId === capture.id ||
      (submission.captureId === null && submission.textHash === textHash);
    if (!belongsToCapture) return [submission];
    return terminal ? [] : [{ ...submission, captureId: capture.id }];
  });
  return writeInboxTrackingState(
    userId,
    {
      ...state,
      captureIds: [...state.captureIds, capture.id],
      pendingSubmissions,
    },
    now,
  );
}

function resolveCapture(
  userId: string,
  capture: TextCapture,
  now = Date.now(),
): InboxTrackingState {
  const state = readInboxTrackingState(userId, now);
  const pendingSubmissions =
    capture.processingStatus === "processing"
      ? state.pendingSubmissions
      : state.pendingSubmissions.filter(
          (submission) => submission.captureId !== capture.id,
        );
  return writeInboxTrackingState(
    userId,
    {
      ...state,
      captureIds: [...state.captureIds, capture.id],
      pendingSubmissions,
    },
    now,
  );
}

function removeCapture(
  userId: string,
  captureId: string,
  now = Date.now(),
): InboxTrackingState {
  const state = readInboxTrackingState(userId, now);
  return writeInboxTrackingState(
    userId,
    {
      ...state,
      captureIds: state.captureIds.filter((id) => id !== captureId),
      pendingSubmissions: state.pendingSubmissions.filter(
        (submission) => submission.captureId !== captureId,
      ),
    },
    now,
  );
}

export function useInboxTracking(userId: string) {
  const [state, setState] = useState<InboxTrackingState>(emptyState);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      setState(readInboxTrackingState(userId));
    }, 0);
    return () => window.clearTimeout(timeoutId);
  }, [userId]);

  const beginSubmission = useCallback(
    async (text: string) => {
      try {
        const submission = await getOrCreatePendingSubmission(userId, text);
        setState(readInboxTrackingState(userId));
        return submission;
      } catch {
        return {
          textHash: "",
          idempotencyKey: crypto.randomUUID(),
          createdAt: new Date().toISOString(),
          captureId: null,
        };
      }
    },
    [userId],
  );

  const rememberCapture = useCallback(
    (submission: PendingCaptureSubmission, capture: TextCapture) => {
      setState(attachCapture(userId, submission, capture));
    },
    [userId],
  );

  const rememberRecoveredCapture = useCallback(
    async (capture: TextCapture) => {
      try {
        setState(await reconcileRecoveredCapture(userId, capture));
      } catch {
        setState(addCaptureIds(userId, [capture.id]));
      }
    },
    [userId],
  );

  const markCaptureResolved = useCallback(
    (capture: TextCapture) => {
      setState(resolveCapture(userId, capture));
    },
    [userId],
  );

  const dismissCapture = useCallback(
    (captureId: string) => {
      setState(removeCapture(userId, captureId));
    },
    [userId],
  );

  const unresolvedSubmissionCount = useMemo(
    () =>
      state.pendingSubmissions.filter(
        (submission) => submission.captureId === null,
      ).length,
    [state.pendingSubmissions],
  );

  return {
    state,
    unresolvedSubmissionCount,
    beginSubmission,
    rememberCapture,
    rememberRecoveredCapture,
    markCaptureResolved,
    dismissCapture,
  };
}
