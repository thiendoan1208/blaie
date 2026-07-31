package com.blaie.blaie_be.capture.benchmark;

import com.blaie.blaie_be.capture.domain.ClassifiedTextItem;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CaptureBenchmarkScorer {
    private CaptureBenchmarkScorer() {
    }

    static CaseScore score(
            List<CaptureBenchmarkDataset.ExpectedItem> expected,
            List<ClassifiedTextItem> actual
    ) {
        if (expected.isEmpty() && actual.isEmpty()) {
            return new CaseScore(true, 1.0, 1.0, List.of());
        }

        List<String> failures = new ArrayList<>();
        boolean structureCorrect = expected.size() == actual.size();
        if (!structureCorrect) {
            failures.add("expected " + expected.size() + " items but got " + actual.size());
        }

        int compared = Math.min(expected.size(), actual.size());
        int correctCategories = 0;
        int requiredFragments = 0;
        int matchedFragments = 0;
        for (int index = 0; index < compared; index++) {
            var expectedItem = expected.get(index);
            var actualItem = actual.get(index);
            if (expectedItem.category() == actualItem.category()) {
                correctCategories++;
            } else {
                failures.add("item " + index + " category expected "
                        + expectedItem.category().value() + " but got " + actualItem.category().value());
            }

            String normalizedText = normalize(actualItem.originalText());
            for (String fragment : expectedItem.mustContain()) {
                requiredFragments++;
                if (normalizedText.contains(normalize(fragment))) {
                    matchedFragments++;
                } else {
                    failures.add("item " + index + " missing fragment: " + fragment);
                }
            }
        }
        for (int index = compared; index < expected.size(); index++) {
            requiredFragments += expected.get(index).mustContain().size();
        }

        double categoryAccuracy = expected.isEmpty()
                ? 0.0
                : (double) correctCategories / expected.size();
        double evidenceRecall = requiredFragments == 0
                ? (structureCorrect ? 1.0 : 0.0)
                : (double) matchedFragments / requiredFragments;
        boolean exact = structureCorrect
                && correctCategories == expected.size()
                && matchedFragments == requiredFragments;
        return new CaseScore(exact, categoryAccuracy, evidenceRecall, failures);
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^\\p{L}\\p{N}_]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    record CaseScore(
            boolean exact,
            double categoryAccuracy,
            double evidenceRecall,
            List<String> failures
    ) {
        CaseScore {
            failures = List.copyOf(failures);
        }
    }
}
