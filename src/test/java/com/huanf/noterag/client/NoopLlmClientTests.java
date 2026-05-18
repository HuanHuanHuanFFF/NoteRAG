package com.huanf.noterag.client;

import com.huanf.noterag.rag.RagPrompt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class NoopLlmClientTests {

    private final NoopLlmClient client = new NoopLlmClient();

    @Test
    void chatReturnsEmptyAnswer() {
        String answer = client.chat(new RagPrompt("system", "user"));

        assertThat(answer).isEmpty();
    }

    @Test
    void chatRejectsNullPrompt() {
        assertThatThrownBy(() -> client.chat(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("prompt must not be null");
    }
}
