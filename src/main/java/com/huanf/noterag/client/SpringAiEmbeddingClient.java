package com.huanf.noterag.client;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.embedding.EmbeddingModel;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;

public class SpringAiEmbeddingClient implements EmbeddingClient {

    private static final String EMBEDDING_FAILED_MESSAGE = "Embedding 服务调用失败";

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingClient(EmbeddingModel embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel must not be null");
    }

    @Override
    public float[] embed(String text) {
        validateText(text);

        try {
            return embeddingModel.embed(text);
        } catch (RuntimeException ex) {
            throw new BusinessException(CodeStatus.EMBEDDING_FAILED, EMBEDDING_FAILED_MESSAGE, ex);
        }
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        validateTexts(texts);
        if (texts.isEmpty()) {
            return List.of();
        }

        try {
            return embeddingModel.embed(texts);
        } catch (RuntimeException ex) {
            throw new BusinessException(CodeStatus.EMBEDDING_FAILED, EMBEDDING_FAILED_MESSAGE, ex);
        }
    }

    private void validateTexts(List<String> texts) {
        if (texts == null) {
            throw new IllegalArgumentException("texts must not be null");
        }
        for (String text : texts) {
            validateText(text);
        }
    }

    private void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be null or blank");
        }
    }

}
