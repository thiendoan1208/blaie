import { isAppError } from "@/shared/api/errors/app-error";

export function transcriptionRequestErrorMessage(error: unknown): string {
  if (!isAppError(error)) {
    return "Unable to transcribe this recording. Your recording is still available to retry.";
  }

  switch (error.code) {
    case "AUDIO_TOO_LARGE":
      return "The recording is too large. Record a shorter message.";
    case "AUDIO_TYPE_UNSUPPORTED":
      return "This browser produced an unsupported audio format.";
    case "TRANSCRIPTION_EMPTY":
      return "No speech was detected. Please record again.";
    case "TRANSCRIPTION_TOO_LONG":
      return "The transcript is too long for one capture. Record a shorter message.";
    case "TRANSCRIPTION_NOT_CONFIGURED":
      return "Voice capture is not configured yet.";
    case "RATE_LIMITED": {
      const retry = error.retryAfterSeconds
        ? ` Try again in ${error.retryAfterSeconds} seconds.`
        : " Try again shortly.";
      return `Too many voice requests.${retry}`;
    }
    case "TRANSCRIPTION_UNAVAILABLE":
    case "SERVICE_UNAVAILABLE":
    case "TIMEOUT":
    case "NETWORK_ERROR":
      return "Transcription is temporarily unavailable. Your recording is still available to retry.";
    default:
      return error.message || "Unable to transcribe this recording.";
  }
}
