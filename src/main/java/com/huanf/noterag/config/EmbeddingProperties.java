package com.huanf.noterag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "noterag.embedding")
@Validated
public class EmbeddingProperties {

    /**
     * 是否启用 NoteRAG 的向量化能力。
     *
     * <p>本地和普通测试默认关闭，避免未配置外部模型时影响 import/chunk 调试。
     * Docker/线上环境应显式开启；开启后启动期会校验 Spring AI 是否已经创建 EmbeddingModel。</p>
     */
    private boolean enabled;

    /**
     * NoteRAG 记录到 embedding_models 表中的供应商名称，不直接决定 Spring AI 使用哪个自动配置。
     */
    private String provider = "openai";

    /**
     * 当前向量模型名称，需要与 Spring AI 的 embedding model 配置以及 embedding_models 表保持一致。
     */
    private String modelName;

    /**
     * 当前向量维度。v1 只支持写入 chunk_embeddings_1024。
     */
    private Integer dimension = 1024;

    /**
     * 当前向量距离度量。v1 使用 cosine，并与 pgvector HNSW 索引保持一致。
     */
    private String distanceMetric = "cosine";

    @AssertTrue(message = "provider must not be blank when embedding is enabled")
    public boolean isProviderValidWhenEnabled() {
        return !enabled || hasText(provider);
    }

    @AssertTrue(message = "modelName must not be blank when embedding is enabled")
    public boolean isModelNameValidWhenEnabled() {
        return !enabled || hasText(modelName);
    }

    @AssertTrue(message = "distanceMetric must not be blank when embedding is enabled")
    public boolean isDistanceMetricValidWhenEnabled() {
        return !enabled || hasText(distanceMetric);
    }

    @AssertTrue(message = "dimension must be greater than 0 when embedding is enabled")
    public boolean isDimensionValidWhenEnabled() {
        return !enabled || (dimension != null && dimension > 0);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
