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

    private boolean enabled;

    private String provider = "openai";

    private String modelName;

    private Integer dimension = 1024;

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
