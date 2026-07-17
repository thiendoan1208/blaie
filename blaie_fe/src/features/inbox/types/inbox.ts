export type InboxCategory =
  | "task"
  | "calendar_event"
  | "reminder"
  | "information";

export type InboxProcessingStatus = "processing" | "completed" | "failed";

export type InboxItem = {
  id: string;
  originalText: string;
  category: InboxCategory | null;
  processingStatus: InboxProcessingStatus;
  createdAt: string;
};

export type CreateTextCaptureInput = {
  text: string;
  idempotencyKey: string;
};

export type TextCapture = {
  id: string;
  originalText: string;
  processingStatus: InboxProcessingStatus;
  failureCode: string | null;
  canRetry: boolean;
  items: InboxItem[];
  createdAt: string;
  updatedAt: string;
};
