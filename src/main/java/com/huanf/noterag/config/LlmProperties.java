package com.huanf.noterag.config;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * LLM 问答阶段配置。
 *
 * <p>本地开发默认关闭，关闭时仍可继续调试 retrieval/rerank 或后续 chat 链路中
 * 不依赖 LLM 的部分。开启后必须配置 Spring AI ChatModel 所需参数。</p>
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "noterag.llm")
@Validated
public class LlmProperties {

    private boolean enabled = false;

    private String provider = "openai";

    private String modelName = "";

    private String apiKey = "";

    private String baseUrl = "";

    private Double temperature = 0.2;

    @AssertTrue(message = "noterag.llm.provider must not be blank when llm is enabled")
    public boolean isProviderValidWhenEnabled() {
        return !enabled || hasText(provider);
    }

    @AssertTrue(message = "noterag.llm.model-name must not be blank when llm is enabled")
    public boolean isModelNameValidWhenEnabled() {
        return !enabled || hasText(modelName);
    }

    @AssertTrue(message = "noterag.llm.api-key must not be blank when llm is enabled")
    public boolean isApiKeyValidWhenEnabled() {
        return !enabled || hasText(apiKey);
    }

    @AssertTrue(message = "noterag.llm.base-url must not be blank when llm is enabled")
    public boolean isBaseUrlValidWhenEnabled() {
        return !enabled || hasText(baseUrl);
    }

    @AssertTrue(message = "noterag.llm.temperature must be between 0 and 2")
    public boolean isTemperatureValid() {
        return temperature != null && temperature >= 0 && temperature <= 2;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
