package com.huanf.noterag.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Rerank 阶段参数配置。
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "noterag.rerank")
@Validated
public class RerankProperties {

    /**
     * 是否启用外部 rerank 调用。本地开发可设为 false，线上建议 true；
     * false 时不会调用外部 rerank 服务，直接按向量召回顺序截断。
     */
    private boolean enabled = false;

    private String provider = "dashscope";

    private String model = "qwen3-rerank";

    private String apiKey = "";

    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-api/v1";

    @Min(1)
    private int defaultTopK = 5;

    @Min(1)
    private int maxTopK = 20;

    @Min(1)
    private int maxDocuments = 500;

    private String instruct = "给定一个技术问题，从候选 Markdown 技术笔记片段中找出最能回答该问题的片段。";

    @AssertTrue(message = "noterag.rerank.api-key must not be blank when rerank is enabled")
    public boolean isApiKeyValidWhenEnabled() {
        return !enabled || hasText(apiKey);
    }

    @AssertTrue(message = "noterag.rerank.model must not be blank when rerank is enabled")
    public boolean isModelValidWhenEnabled() {
        return !enabled || hasText(model);
    }

    @AssertTrue(message = "noterag.rerank.base-url must not be blank when rerank is enabled")
    public boolean isBaseUrlValidWhenEnabled() {
        return !enabled || hasText(baseUrl);
    }

    @AssertTrue(message = "noterag.rerank.default-top-k must be less than or equal to max-top-k")
    public boolean isDefaultTopKNotGreaterThanMaxTopK() {
        return defaultTopK <= maxTopK;
    }

    @AssertTrue(message = "noterag.rerank.max-documents must be greater than or equal to max-top-k")
    public boolean isMaxDocumentsNotLessThanMaxTopK() {
        return maxDocuments >= maxTopK;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
