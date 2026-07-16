package com.blaie.blaie_be.capture.infrastructure.deepseek;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekTextClassifierAdapterTest {
    @Test
    void promptDefinesCurrentCaptureClassificationContract() {
        assertThat(DeepSeekTextClassifierAdapter.PROMPT_VERSION).isEqualTo("v4");
        assertThat(DeepSeekTextClassifierAdapter.SYSTEM_PROMPT)
                .contains("Split one personal Inbox capture into every independent record")
                .contains("Return JSON only")
                .contains("a request addressed to the assistant")
                .contains("The latest explicit decision wins")
                .contains("cancels, rejects, negates")
                .contains("Return {\"items\":[]} when the capture contains no active record")
                .contains("reminder: only when the user explicitly asks the system to remind or notify them")
                .contains("calendar_event: a scheduled meeting, appointment, or event")
                .contains("information: a question or a request addressed to the assistant")
                .contains("task: an action the user intends, needs, plans, or commits to perform themselves")
                .contains("Do not add markdown, explanations, or extra keys.");
    }
}
