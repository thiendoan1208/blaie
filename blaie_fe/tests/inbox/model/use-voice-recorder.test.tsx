import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useVoiceRecorder } from "@/features/inbox/model/use-voice-recorder";

class FakeMediaRecorder {
  static isTypeSupported = vi.fn(() => true);
  static instances: FakeMediaRecorder[] = [];

  readonly mimeType: string;
  state: RecordingState = "inactive";
  ondataavailable: ((event: BlobEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onstop: (() => void) | null = null;
  stop = vi.fn(() => {
    this.state = "inactive";
    this.ondataavailable?.({
      data: new Blob(["voice"], { type: this.mimeType }),
    } as BlobEvent);
    this.onstop?.();
  });
  start = vi.fn(() => {
    this.state = "recording";
  });

  constructor(
    readonly stream: MediaStream,
    options?: MediaRecorderOptions,
  ) {
    this.mimeType = options?.mimeType ?? "audio/webm";
    FakeMediaRecorder.instances.push(this);
  }
}

describe("useVoiceRecorder", () => {
  const trackStop = vi.fn();
  const getUserMedia = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    FakeMediaRecorder.instances = [];
    FakeMediaRecorder.isTypeSupported.mockClear();
    trackStop.mockReset();
    getUserMedia.mockReset();
    getUserMedia.mockResolvedValue({
      getTracks: () => [{ stop: trackStop }],
    } as unknown as MediaStream);
    vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("requests microphone permission, records, and returns a blob on Send", async () => {
    const { result } = renderHook(() => useVoiceRecorder());

    await act(async () => {
      await result.current.start();
    });

    expect(getUserMedia).toHaveBeenCalledWith({ audio: true });
    expect(result.current.state).toBe("recording");

    let audio!: Blob;
    await act(async () => {
      audio = await result.current.stop();
    });

    expect(audio.type).toBe("audio/webm;codecs=opus");
    expect(trackStop).toHaveBeenCalledOnce();
    expect(result.current.state).toBe("idle");
  });

  it("cancels without returning reusable audio and releases the microphone", async () => {
    const { result } = renderHook(() => useVoiceRecorder());
    await act(async () => {
      await result.current.start();
    });

    act(() => result.current.cancel());

    expect(FakeMediaRecorder.instances[0]?.stop).toHaveBeenCalledOnce();
    expect(trackStop).toHaveBeenCalledOnce();
    expect(result.current.state).toBe("idle");
  });

  it("automatically stops at sixty seconds", async () => {
    const onMaximumDuration = vi.fn();
    const { result } = renderHook(() =>
      useVoiceRecorder({ onMaximumDuration }),
    );
    await act(async () => {
      await result.current.start();
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    expect(FakeMediaRecorder.instances[0]?.stop).toHaveBeenCalledOnce();
    expect(onMaximumDuration).toHaveBeenCalledOnce();
    expect(trackStop).toHaveBeenCalledOnce();
  });

  it("surfaces permission denial and remains ready for another attempt", async () => {
    getUserMedia.mockRejectedValue(
      new DOMException("Permission denied", "NotAllowedError"),
    );
    const { result } = renderHook(() => useVoiceRecorder());

    await act(async () => {
      await expect(result.current.start()).rejects.toMatchObject({
        name: "NotAllowedError",
      });
    });

    expect(result.current.state).toBe("idle");
    expect(result.current.error).toBe("permission_denied");
  });
});
