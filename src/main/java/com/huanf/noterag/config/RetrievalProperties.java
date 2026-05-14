package com.huanf.noterag.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 检索阶段参数配置。
 *
 * <p>defaultTopN 用于接口未显式传入召回数量时的默认值，maxTopN 用于限制单次向量召回规模。</p>
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "noterag.retrieval")
@Validated
public class RetrievalProperties {

    @Min(1)
    private int defaultTopN = 20;

    @Min(1)
    private int maxTopN = 50;

    /**
     * 默认召回数不能大于允许的最大召回数。
     */
    @AssertTrue(message = "noterag.retrieval.default-top-n must be less than or equal to max-top-n")
    public boolean isDefaultTopNNotGreaterThanMaxTopN() {
        return defaultTopN <= maxTopN;
    }
}
