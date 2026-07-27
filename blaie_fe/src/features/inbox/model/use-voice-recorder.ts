"use client";

import { useCallback, useEffect, useRef, useState } from "react";

const MAX_RECORDING_DURATION_MS = 60_000;
const MIME_TYPE_CANDIDATES = [
  "audio/webm;codecs=opus",
  "audio/mp4",
] as const;

export type VoiceRecorderState =
  | "idle"
  | "requesting_permission"
  | "recording";

export type VoiceRecorderError =
  | "permission_denied"
  | "unsupported"
  | "recording_failed"
  | null;

type StopResolver = {
  resolve: (audio: Blob) => void;
  reject: (error: Error) => void;
};

export function useVoiceRecorder({
  onMaximumDuration,
}: {
  onMaximumDuration?: (audio: Blob) => void;
} = {}) {
  const [state, setState] = useState<VoiceRecorderState>("idle");
  const [error, setError] = useState<VoiceRecorderError>(null);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const stopResolverRef = useRef<StopResolver | null>(null);
  const discardRef = useRef(false);
  const automaticStopRef = useRef(false);
  const durationTimerRef = useRef<number | null>(null);
  const elapsedTimerRef = useRef<number | null>(null);
  const startedAtRef = useRef(0);
  const mountedRef = useRef(true);
  const maximumDurationCallbackRef = useRef(onMaximumDuration);

  useEffect(() => {
    maximumDurationCallbackRef.current = onMaximumDuration;
  }, [onMaximumDuration]);

  const clearTimers = useCallback(() => {
    if (durationTimerRef.current !== null) {
      window.clearTimeout(durationTimerRef.current);
      durationTimerRef.current = null;
    }
    if (elapsedTimerRef.current !== null) {
      window.clearInterval(elapsedTimerRef.current);
      elapsedTimerRef.current = null;
    }
  }, []);

  const releaseStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }, []);

  const resetRecorder = useCallback(() => {
    clearTimers();
    releaseStream();
    recorderRef.current = null;
    chunksRef.current = [];
    if (mountedRef.current) {
      setState("idle");
      setElapsedSeconds(0);
    }
  }, [clearTimers, releaseStream]);

  const start = useCallback(async () => {
    if (recorderRef.current?.state === "recording") return;
    if (
      typeof MediaRecorder === "undefined" ||
      !navigator.mediaDevices?.getUserMedia
    ) {
      setError("unsupported");
      throw new Error("Voice recording is not supported by this browser");
    }

    setError(null);
    setState("requesting_permission");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!mountedRef.current) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }

      const mimeType = MIME_TYPE_CANDIDATES.find((candidate) =>
        MediaRecorder.isTypeSupported(candidate),
      );
      const recorder = mimeType
        ? new MediaRecorder(stream, { mimeType })
        : new MediaRecorder(stream);
      streamRef.current = stream;
      recorderRef.current = recorder;
      chunksRef.current = [];
      discardRef.current = false;
      automaticStopRef.current = false;
      stopResolverRef.current = null;

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) chunksRef.current.push(event.data);
      };
      recorder.onerror = () => {
        const resolver = stopResolverRef.current;
        stopResolverRef.current = null;
        resetRecorder();
        if (mountedRef.current) setError("recording_failed");
        resolver?.reject(new Error("Voice recording failed"));
      };
      recorder.onstop = () => {
        const audio = new Blob(chunksRef.current, {
          type:
            recorder.mimeType ||
            chunksRef.current[0]?.type ||
            "audio/webm",
        });
        const resolver = stopResolverRef.current;
        const discarded = discardRef.current;
        const automatic = automaticStopRef.current;
        stopResolverRef.current = null;
        resetRecorder();
        if (discarded) return;
        if (automatic) {
          maximumDurationCallbackRef.current?.(audio);
          return;
        }
        resolver?.resolve(audio);
      };

      recorder.start();
      startedAtRef.current = Date.now();
      setElapsedSeconds(0);
      setState("recording");
      elapsedTimerRef.current = window.setInterval(() => {
        const seconds = Math.min(
          60,
          Math.floor((Date.now() - startedAtRef.current) / 1_000),
        );
        if (mountedRef.current) setElapsedSeconds(seconds);
      }, 250);
      durationTimerRef.current = window.setTimeout(() => {
        const activeRecorder = recorderRef.current;
        if (activeRecorder?.state !== "recording") return;
        automaticStopRef.current = true;
        clearTimers();
        activeRecorder.stop();
      }, MAX_RECORDING_DURATION_MS);
    } catch (cause) {
      releaseStream();
      if (mountedRef.current) {
        setState("idle");
        setError(
          cause instanceof DOMException && cause.name === "NotAllowedError"
            ? "permission_denied"
            : "recording_failed",
        );
      }
      throw cause;
    }
  }, [clearTimers, releaseStream, resetRecorder]);

  const stop = useCallback((): Promise<Blob> => {
    const recorder = recorderRef.current;
    if (!recorder || recorder.state !== "recording") {
      return Promise.reject(new Error("No voice recording is active"));
    }
    clearTimers();
    discardRef.current = false;
    automaticStopRef.current = false;
    return new Promise<Blob>((resolve, reject) => {
      stopResolverRef.current = { resolve, reject };
      recorder.stop();
    });
  }, [clearTimers]);

  const cancel = useCallback(() => {
    const recorder = recorderRef.current;
    discardRef.current = true;
    stopResolverRef.current = null;
    clearTimers();
    if (recorder?.state === "recording") {
      recorder.stop();
    } else {
      resetRecorder();
    }
  }, [clearTimers, resetRecorder]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      discardRef.current = true;
      clearTimers();
      const recorder = recorderRef.current;
      if (recorder?.state === "recording") recorder.stop();
      releaseStream();
    };
  }, [clearTimers, releaseStream]);

  return {
    state,
    error,
    elapsedSeconds,
    start,
    stop,
    cancel,
  };
}
