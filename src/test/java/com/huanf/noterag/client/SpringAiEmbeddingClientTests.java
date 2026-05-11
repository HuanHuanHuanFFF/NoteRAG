package com.huanf.noterag.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;

class SpringAiEmbeddingClientTests {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final SpringAiEmbeddingClient embeddingClient = new SpringAiEmbeddingClient(embeddingModel);

    @Test
    void embedReturnsEmbedding() {
        when(embeddingModel.embed("hello"))
                .thenReturn(new float[] {1.0f, 2.5f});

        float[] embedding = embeddingClient.embed("hello");

        assertThat(embedding).containsExactly(1.0f, 2.5f);
    }

    @Test
    void embedAllKeepsBatchOrder() {
        when(embeddingModel.embed(List.of("first", "second")))
                .thenReturn(List.of(new float[] {1.0f, 1.5f}, new float[] {2.0f, 2.5f}));

        List<float[]> embeddings = embeddingClient.embedAll(List.of("first", "second"));

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0)).containsExactly(1.0f, 1.5f);
        assertThat(embeddings.get(1)).containsExactly(2.0f, 2.5f);
    }

    @Test
    void embedAllReturnsEmptyForEmptyInput() {
        List<float[]> embeddings = embeddingClient.embedAll(List.of());

        assertThat(embeddings).isEmpty();
        verifyNoInteractions(embeddingModel);
    }

    @Test
    void embedRejectsNullOrBlankText() {
        assertThatThrownBy(() -> embeddingClient.embed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text must not be null or blank");
        assertThatThrownBy(() -> embeddingClient.embed("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text must not be null or blank");
    }

    @Test
    void embedAllRejectsNullOrBlankText() {
        assertThatThrownBy(() -> embeddingClient.embedAll(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("texts must not be null");
        assertThatThrownBy(() -> embeddingClient.embedAll(java.util.Arrays.asList("ok", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text must not be null or blank");
        assertThatThrownBy(() -> embeddingClient.embedAll(List.of("ok", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text must not be null or blank");
    }

    @Test
    void embedAllWrapsSpringAiFailureAsBusinessException() {
        RuntimeException cause = new RuntimeException("provider unavailable");
        when(embeddingModel.embed(List.of("hello"))).thenThrow(cause);

        assertThatThrownBy(() -> embeddingClient.embedAll(List.of("hello")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.EMBEDDING_FAILED);
                    assertThat(exception).hasMessage("Embedding 服务调用失败");
                    assertThat(exception).hasCause(cause);
                });
    }
}
