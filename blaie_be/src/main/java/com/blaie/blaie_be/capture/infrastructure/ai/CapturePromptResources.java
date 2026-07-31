package com.blaie.blaie_be.capture.infrastructure.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class CapturePromptResources {
    public static final String POLICY_VERSION = "classification-v1";
    public static final String DEEPSEEK_PROMPT_VERSION = "text-v6";
    public static final String GEMINI_PROMPT_VERSION = "image-v1";

    private static final String POLICY = read("prompts/capture/classification-policy-v1.txt");
    private static final String DEEPSEEK = read("prompts/capture/deepseek-text-v6.txt");
    private static final String GEMINI = read("prompts/capture/gemini-image-v1.txt");

    private CapturePromptResources() {
    }

    public static String deepSeekSystemPrompt() {
        return combine(POLICY, DEEPSEEK);
    }

    public static String geminiPrompt() {
        return GEMINI.strip();
    }

    private static String combine(String policy, String providerInstructions) {
        return policy.strip() + System.lineSeparator().repeat(2) + providerInstructions.strip();
    }

    private static String read(String path) {
        ClassLoader classLoader = CapturePromptResources.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Required Capture prompt resource is missing: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Required Capture prompt resource could not be read: " + path, exception);
        }
    }
}
