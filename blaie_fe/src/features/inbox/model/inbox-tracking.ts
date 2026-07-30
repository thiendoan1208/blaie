"use client";

import { useCallback, useEffect, useMemo, useState } from "react";

import type { TextCapture } from "../types/inbox";

const STORAGE_PREFIX = "blaie.inbox.capture-tracking.v2";
const STATE_VERSION = 2;
const MAX_TRACKED_CAPTURES = 100;
const IDEMPOTENCY_TTL_MS = 24 * 60 * 60 * 1_000;
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export type PendingCaptureSubmission = {
  inputType: "text" | "image";
  requestHash: string;
  idempotencyKey: string;
  createdAt: string;
  captureId: string | null;
};

export type InboxTrackingState = {
  version: 2;
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

export function clearAllInboxTracking(): void {
  const storage = browserStorage();
  if (!storage) return;
  try {
    const keys: string[] = [];
    for (let index = 0; index < storage.length; index++) {
      const key = storage.key(index);
      if (key?.startsWith(`${STORAGE_PREFIX}:`)) keys.push(key);
    }
    keys.forEach((key) => storage.removeItem(key));
  } catch {
    // Logout still succeeds if browser privacy settings block storage access.
  }
}

function isPendingSubmission(value: unknown): value is PendingCaptureSubmission {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return (
    (candidate.inputType === "text" || candidate.inputType === "image") &&
    typeof candidate.requestHash === "string" &&
    /^[a-f0-9]{64}$/.test(candidate.requestHash) &&
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
  // Match Java String.trim() used by the capture admission service exactly.
  return text.replace(/^[\u0000-\u0020]+|[\u0000-\u0020]+$/g, "");
}

export async function hashCaptureText(text: string): Promise<string> {
  const bytes = new TextEncoder().encode(normalizeCaptureText(text));
  return hashBytes(bytes);
}

async function hashBytes(bytes: Uint8Array<ArrayBuffer>): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)]
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("");
}

async function readFileBytes(file: File): Promise<Uint8Array<ArrayBuffer>> {
  if (typeof file.arrayBuffer === "function") {
    return new Uint8Array(await file.arrayBuffer());
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
    reader.readAsArrayBuffer(file);
  });
}

export async function hashCaptureSubmission(
  text: string,
  image?: File,
): Promise<string> {
  if (!image) return hashCaptureText(text);
  const normalizedText = new TextEncoder().encode(normalizeCaptureText(text));
  const imageBytes = await readFileBytes(image);
  const prefix = new TextEncoder().encode("image-v1");
  const bytes = new Uint8Array(
    prefix.length + 4 + normalizedText.length + imageBytes.length,
  );
  bytes.set(prefix, 0);
  new DataView(bytes.buffer).setUint32(prefix.length, normalizedText.length);
  bytes.set(normalizedText, prefix.length + 4);
  bytes.set(imageBytes, prefix.length + 4 + normalizedText.length);
  return hashBytes(bytes);
}

export async function getOrCreatePendingSubmission(
  userId: string,
  text: string,
  imageOrNow?: File | number,
  requestedNow = Date.now(),
): Promise<PendingCaptureSubmission> {
  const image = imageOrNow instanceof File ? imageOrNow : undefined;
  const now = typeof imageOrNow === "number" ? imageOrNow : requestedNow;
  const requestHash = await hashCaptureSubmission(text, image);
  const inputType = image ? "image" : "text";
  const state = readInboxTrackingState(userId, now);
  const existing = state.pendingSubmissions.find(
    (submission) =>
      submission.inputType === inputType &&
      submission.requestHash === requestHash,
  );
  if (existing) return existing;

  const submission: PendingCaptureSubmission = {
    inputType,
    requestHash,
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
  idempotencyKey?: string,
  now = Date.now(),
): Promise<InboxTrackingState> {
  const textHash =
    capture.inputType === "text" && capture.originalText
      ? await hashCaptureText(capture.originalText)
      : null;
  const state = readInboxTrackingState(userId, now);
  const terminal = capture.processingStatus !== "processing";
  const pendingSubmissions = state.pendingSubmissions.flatMap((submission) => {
    const belongsToCapture =
      submission.captureId === capture.id ||
      submission.idempotencyKey === idempotencyKey ||
      (submission.captureId === null &&
        submission.inputType === "text" &&
        submission.requestHash === textHash);
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

function removeSubmission(
  userId: string,
  idempotencyKey: string,
  now = Date.now(),
): InboxTrackingState {
  const state = readInboxTrackingState(userId, now);
  return writeInboxTrackingState(
    userId,
    {
      ...state,
      pendingSubmissions: state.pendingSubmissions.filter(
        (submission) => submission.idempotencyKey !== idempotencyKey,
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
    async (
      text: string,
      image?: File,
    ): Promise<PendingCaptureSubmission> => {
      try {
        const submission = await getOrCreatePendingSubmission(userId, text, image);
        setState(readInboxTrackingState(userId));
        return submission;
      } catch {
        return {
          inputType: image ? "image" : "text",
          requestHash: "",
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
    async (capture: TextCapture, idempotencyKey?: string) => {
      try {
        setState(await reconcileRecoveredCapture(userId, capture, idempotencyKey));
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

  const discardSubmission = useCallback(
    (idempotencyKey: string) => {
      setState(removeSubmission(userId, idempotencyKey));
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
    discardSubmission,
    rememberCapture,
    rememberRecoveredCapture,
    markCaptureResolved,
    dismissCapture,
  };
}
