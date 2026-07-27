"use client";

import { LoaderCircle, Mic, RotateCcw, Send, X } from "lucide-react";
import { useCallback, useEffect, useRef } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";

import { useVoiceRecorder } from "../model/use-voice-recorder";

export type VoiceCapturePhase = "idle" | "transcribing" | "saving";

export function VoiceRecorder({
  disabled,
  failureMessage,
  phase,
  onNewRecording,
  onRecordingChange,
  onRetry,
  onSend,
}: {
  disabled: boolean;
  failureMessage: string | null;
  phase: VoiceCapturePhase;
  onNewRecording: () => void;
  onRecordingChange: (recording: boolean) => void;
  onRetry: () => Promise<void>;
  onSend: (audio: Blob) => Promise<void>;
}) {
  const sendInFlight = useRef(false);
  const sendAudio = useCallback(
    async (audio: Blob) => {
      if (sendInFlight.current) return;
      sendInFlight.current = true;
      try {
        await onSend(audio);
      } finally {
        sendInFlight.current = false;
      }
    },
    [onSend],
  );
  const recorder = useVoiceRecorder({
    onMaximumDuration: (audio) => void sendAudio(audio),
  });

  useEffect(() => {
    onRecordingChange(recorder.state === "recording");
  }, [onRecordingChange, recorder.state]);

  async function start() {
    onNewRecording();
    try {
      await recorder.start();
    } catch (cause) {
      const message =
        cause instanceof Error && cause.message.includes("not supported")
          ? "Voice recording is not supported by this browser."
          : cause instanceof DOMException && cause.name === "NotAllowedError"
            ? "Microphone permission was denied. Allow microphone access in your browser settings."
            : "Unable to start voice recording. Check your microphone and try again.";
      toast.error(message);
    }
  }

  async function send() {
    if (sendInFlight.current) return;
    try {
      const audio = await recorder.stop();
      await sendAudio(audio);
    } catch {
      toast.error("Unable to finish this recording. Please record again.");
    }
  }

  if (recorder.state === "requesting_permission") {
    return (
      <Button disabled type="button" variant="outline">
        <LoaderCircle className="animate-spin" />
        Requesting microphone…
      </Button>
    );
  }

  if (recorder.state === "recording") {
    return (
      <div
        aria-label="Voice recording controls"
        className="flex flex-wrap items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 dark:border-red-950 dark:bg-red-950/30"
      >
        <span className="size-2 animate-pulse rounded-full bg-red-500" />
        <span className="mr-auto text-sm font-medium">
          Recording {formatDuration(recorder.elapsedSeconds)}
        </span>
        <Button onClick={recorder.cancel} type="button" variant="ghost">
          <X />
          Cancel
        </Button>
        <Button onClick={() => void send()} type="button">
          <Send />
          Send
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center justify-end gap-2">
        {phase !== "idle" && (
          <span
            aria-live="polite"
            className="mr-auto flex items-center gap-2 text-sm text-muted-foreground"
          >
            <LoaderCircle className="size-4 animate-spin" />
            {phase === "transcribing"
              ? "Transcribing your recording…"
              : "Saving your capture…"}
          </span>
        )}
        {failureMessage && phase === "idle" && (
          <>
            <span
              role="alert"
              className="mr-auto text-sm text-destructive"
            >
              {failureMessage}
            </span>
            <Button
              aria-label="Retry voice capture"
              onClick={() => void onRetry()}
              type="button"
              variant="outline"
            >
              <RotateCcw />
              Retry
            </Button>
          </>
        )}
        <Button
          aria-label="Record voice"
          disabled={disabled}
          onClick={() => void start()}
          type="button"
          variant="outline"
        >
          <Mic />
          Voice
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        Voice audio is sent to the configured transcription provider and is not
        retained by Blaie. The resulting transcript is stored as your Capture.
      </p>
    </div>
  );
}

function formatDuration(seconds: number): string {
  return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(
    seconds % 60,
  ).padStart(2, "0")}`;
}
