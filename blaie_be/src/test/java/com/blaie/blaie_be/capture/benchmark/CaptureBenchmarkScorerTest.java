package com.blaie.blaie_be.capture.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.blaie.blaie_be.capture.domain.CaptureCategory;
import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptureBenchmarkScorerTest {
    @Test
    void acceptsCaseAndAccentDifferencesWhileRequiringAllEvidence() {
        var expected = List.of(new CaptureBenchmarkDataset.ExpectedItem(
                CaptureCategory.TASK,
                List.of("Gửi hợp đồng", "__BLAIE_PII_EMAIL_0__", "4 giờ")
        ));

        var score = CaptureBenchmarkScorer.score(
                expected,
                List.of(new ClassifiedTextItem(
                        "gui HOP DONG cho __BLAIE_PII_EMAIL_0__ trước 4 gio",
                        CaptureCategory.TASK
                ))
        );

        assertThat(score.exact()).isTrue();
        assertThat(score.categoryAccuracy()).isEqualTo(1.0);
        assertThat(score.evidenceRecall()).isEqualTo(1.0);
    }

    @Test
    void reportsSplitCategoryAndEvidenceFailuresSeparately() {
        var expected = List.of(
                new CaptureBenchmarkDataset.ExpectedItem(
                        CaptureCategory.CALENDAR_EVENT,
                        List.of("họp team", "9 giờ")
                ),
                new CaptureBenchmarkDataset.ExpectedItem(
                        CaptureCategory.TASK,
                        List.of("gửi báo cáo", "Lan")
                )
        );

        var score = CaptureBenchmarkScorer.score(
                expected,
                List.of(new ClassifiedTextItem("Họp team", CaptureCategory.TASK))
        );

        assertThat(score.exact()).isFalse();
        assertThat(score.categoryAccuracy()).isZero();
        assertThat(score.evidenceRecall()).isEqualTo(0.25);
        assertThat(score.failures()).isNotEmpty();
    }
}
