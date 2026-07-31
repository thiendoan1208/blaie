package com.blaie.blaie_be.capture.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.blaie.blaie_be.capture.domain.CaptureCategory;
import java.util.HashSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CaptureBenchmarkDatasetTest {
    private final CaptureBenchmarkDataset dataset = CaptureBenchmarkDataset.load(new ObjectMapper());

    @Test
    void datasetHasStableVersionCoverageAndUniqueIds() {
        assertThat(dataset.version()).isEqualTo("capture-gold-v1");
        assertThat(dataset.textCases()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(dataset.imageCases()).hasSizeGreaterThanOrEqualTo(6);

        var ids = Stream.concat(
                dataset.textCases().stream().map(CaptureBenchmarkDataset.TextCase::id),
                dataset.imageCases().stream().map(CaptureBenchmarkDataset.ImageCase::id)
        ).toList();
        assertThat(new HashSet<>(ids)).hasSameSizeAs(ids);

        var categories = Stream.concat(
                        dataset.textCases().stream().flatMap(testCase -> testCase.expected().stream()),
                        dataset.imageCases().stream().flatMap(testCase -> testCase.expected().stream())
                )
                .map(CaptureBenchmarkDataset.ExpectedItem::category)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(categories).containsExactlyInAnyOrder(CaptureCategory.values());
    }

    @Test
    void textDatasetCoversTheRiskBoundaries() {
        var tags = dataset.textCases().stream()
                .flatMap(testCase -> testCase.tags().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(tags).contains(
                "boundary-task-reminder",
                "boundary-task-event",
                "boundary-task-information",
                "latest-intent",
                "atomicity",
                "negative",
                "pii",
                "prompt-injection",
                "vi",
                "en"
        );
    }
}
