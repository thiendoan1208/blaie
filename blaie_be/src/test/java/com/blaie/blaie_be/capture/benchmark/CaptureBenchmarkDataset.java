package com.blaie.blaie_be.capture.benchmark;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.blaie.blaie_be.capture.domain.CaptureCategory;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.ObjectMapper;

record CaptureBenchmarkDataset(
        String version,
        List<TextCase> textCases,
        List<ImageCase> imageCases
) {
    private static final String RESOURCE = "ai-benchmark/capture-gold-v1.json";

    CaptureBenchmarkDataset {
        textCases = List.copyOf(textCases);
        imageCases = List.copyOf(imageCases);
    }

    static CaptureBenchmarkDataset load(ObjectMapper objectMapper) {
        try (InputStream input = CaptureBenchmarkDataset.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Benchmark dataset is missing: " + RESOURCE);
            }
            return objectMapper.readValue(input, CaptureBenchmarkDataset.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Benchmark dataset could not be read", exception);
        }
    }

    record TextCase(String id, String input, Set<String> tags, List<ExpectedItem> expected) {
        TextCase {
            tags = Set.copyOf(tags);
            expected = List.copyOf(expected);
        }
    }

    record ImageCase(
            String id,
            String visualText,
            String optionalText,
            Set<String> tags,
            List<ExpectedItem> expected
    ) {
        ImageCase {
            tags = Set.copyOf(tags);
            expected = List.copyOf(expected);
        }
    }

    record ExpectedItem(CaptureCategory category, List<String> mustContain) {
        @JsonCreator
        ExpectedItem(
                @JsonProperty("category") String category,
                @JsonProperty("mustContain") List<String> mustContain
        ) {
            this(CaptureCategory.fromValue(category), mustContain);
        }

        ExpectedItem {
            mustContain = List.copyOf(mustContain);
        }
    }
}
