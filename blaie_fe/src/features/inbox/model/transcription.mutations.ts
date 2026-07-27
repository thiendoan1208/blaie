import { useMutation } from "@tanstack/react-query";

import { transcribeAudio } from "../api/transcription.service";

export function useTranscribeAudioMutation() {
  return useMutation({
    mutationFn: transcribeAudio,
    retry: false,
  });
}
