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
    private int defaultTopK = 8;

    @Min(1)
    private int maxTopK = 20;

    @Min(1)
    private int maxDocuments = 500;

    private String instruct = "给定一个技术问题，请从候选 Markdown 技术笔记片段中选出最能直接回答问题的正文片段。优先选择包含概念解释、机制说明、对比结论或操作要点的片段；降低只包含参考资料、链接列表、目录、图片说明、上下文不完整或不能直接回答问题的片段排名。";

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
