package com.huanf.noterag.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.huanf.noterag.prompt.RagPrompt;

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
