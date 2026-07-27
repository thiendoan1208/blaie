import { beforeEach, describe, expect, it, vi } from "vitest";

import { transcribeAudio } from "@/features/inbox/api/transcription.service";
import { httpClient } from "@/shared/api/http-client";

vi.mock("@/shared/api/http-client", () => ({
  httpClient: {
    post: vi.fn(),
  },
}));

describe("Transcription service", () => {
  beforeEach(() => {
    vi.mocked(httpClient.post).mockReset();
  });

  it("uploads one audio file and returns the transcript", async () => {
    vi.mocked(httpClient.post).mockResolvedValue({
      data: { data: { text: "Nhắc tôi gọi cho mẹ" } },
    });
    const audio = new Blob(["audio"], { type: "audio/webm" });

    await expect(transcribeAudio(audio)).resolves.toEqual({
      text: "Nhắc tôi gọi cho mẹ",
    });

    const [url, body, config] = vi.mocked(httpClient.post).mock.calls[0]!;
    expect(url).toBe("/transcriptions/audio");
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get("file")).toBeInstanceOf(File);
    expect((body as FormData).get("language")).toBe("vi");
    expect(config).toMatchObject({ timeout: 25_000 });
    expect(config?.headers).toBeUndefined();
  });
});
