export type InboxCategory =
  | "task"
  | "calendar_event"
  | "reminder"
  | "information";

export type InboxProcessingStatus = "processing" | "completed" | "failed";
export type CaptureInputType = "text" | "image";

export type InboxItem = {
  id: string;
  captureId: string;
  originalText: string;
  category: InboxCategory | null;
  processingStatus: InboxProcessingStatus;
  createdAt: string;
};

export type InboxPage = {
  items: InboxItem[];
  nextCursor: string | null;
  hasMore: boolean;
  limit: number;
};

export type CreateTextCaptureInput = {
  text: string;
  idempotencyKey: string;
};

export type CreateImageCaptureInput = {
  image: File;
  text?: string;
  idempotencyKey: string;
};

export type CaptureAttachment = {
  id: string;
  type: "image";
  contentType: "image/jpeg" | "image/png" | "image/webp";
  sizeBytes: number;
  width: number;
  height: number;
  contentUrl: string;
};

export type TextCapture = {
  id: string;
  inputType: CaptureInputType;
  originalText: string | null;
  processingStatus: InboxProcessingStatus;
  failureCode: string | null;
  canRetry: boolean;
  attachments: CaptureAttachment[];
  items: InboxItem[];
  createdAt: string;
  updatedAt: string;
};

export type Capture = TextCapture;
