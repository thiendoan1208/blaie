import { isAppError } from "@/shared/api/errors/app-error";

const failureMessages: Record<string, string> = {
  sensitive_credential_detected:
    "This text appears to contain a secret or credential. Remove it and create a new capture.",
  sensitive_personal_identifier_detected:
    "This text appears to contain a payment card or government identifier. Remove it and create a new capture.",
  reserved_privacy_token_detected:
    "This text contains a reserved privacy marker. Remove it and create a new capture.",
  content_policy_blocked:
    "This text cannot be classified under the current content policy.",
  ai_not_configured:
    "The classifier is not configured yet. Try again after the service is configured.",
  ai_provider_not_configured:
    "The classifier is not configured yet. Try again after the service is configured.",
  ai_provider_rejected:
    "The classification provider rejected the request. You can try again later.",
  ai_provider_unavailable:
    "The classification provider is temporarily unavailable. You can try again.",
  ai_invalid_response:
    "The classifier returned an invalid result. You can try again.",
  job_lease_expired:
    "Classification timed out before it could be saved. You can try again.",
  unexpected_classification_error:
    "Classification failed unexpectedly. You can try again.",
};

export function captureFailureMessage(failureCode: string | null): string {
  if (!failureCode) return "Classification failed.";
  return failureMessages[failureCode] ?? "Classification could not be completed.";
}

function retrySuffix(retryAfterSeconds: number | undefined): string {
  if (retryAfterSeconds === undefined) return "";
  if (retryAfterSeconds < 60) {
    return ` Try again in ${Math.max(1, retryAfterSeconds)} seconds.`;
  }
  const minutes = Math.ceil(retryAfterSeconds / 60);
  return ` Try again in about ${minutes} ${minutes === 1 ? "minute" : "minutes"}.`;
}

export function captureRequestErrorMessage(error: unknown): string {
  if (!isAppError(error)) return "Unable to capture this text.";

  const retry = retrySuffix(error.retryAfterSeconds);
  switch (error.code) {
    case "RATE_LIMITED":
      return `Too many capture requests.${retry || " Try again shortly."}`;
    case "TOO_MANY_ACTIVE_JOBS":
      return `Too many captures are still being processed.${retry || " Wait for one to finish and try again."}`;
    case "CAPTURE_PROCESSING_OVERLOADED":
      return `Capture processing is busy.${retry || " Try again later."}`;
    case "CAPTURE_PROCESSING_UNAVAILABLE":
    case "SERVICE_UNAVAILABLE":
      return `Capture processing is temporarily unavailable.${retry || " Try again later."}`;
    case "IDEMPOTENCY_KEY_REUSED":
      return "This saved request key no longer matches the text. Please try again.";
    case "CAPTURE_SENSITIVE_CONTENT":
      return "Remove passwords, API keys, payment cards, or government IDs before capturing this text.";
    default:
      return error.message || "Unable to capture this text.";
  }
}

export function shouldDiscardCaptureSubmission(error: unknown): boolean {
  if (!isAppError(error) || error.status < 400 || error.status >= 500) return false;
  return error.status !== 408 && error.status !== 425 && error.status !== 429;
}
