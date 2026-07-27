import type { ApiResponse } from "@/shared/api/contracts/api-response";
import { httpClient } from "@/shared/api/http-client";

export type AudioTranscription = {
  text: string;
};

const extensionByType: Record<string, string> = {
  "audio/webm": "webm",
  "audio/mp4": "m4a",
  "audio/ogg": "ogg",
  "audio/wav": "wav",
  "audio/mpeg": "mp3",
  "audio/mp3": "mp3",
  "audio/m4a": "m4a",
};

export async function transcribeAudio(
  audio: Blob,
): Promise<AudioTranscription> {
  const contentType = audio.type.split(";", 1)[0] || "audio/webm";
  const extension = extensionByType[contentType] ?? "webm";
  const form = new FormData();
  form.append("file", audio, `recording.${extension}`);
  form.append("language", "en");

  const response = await httpClient.post<ApiResponse<AudioTranscription>>(
    "/transcriptions/audio",
    form,
    { timeout: 25_000 },
  );
  return response.data.data;
}
