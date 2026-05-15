package com.huanf.noterag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

/**
 * Markdown chunk 规则配置。
 *
 * <p>这些参数控制 chunk 的目标大小、硬上限和 overlap，方便在本地调参与测试。</p>
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "noterag.chunking")
@Validated
public class ChunkingProperties {

    @Min(1)
    private int minTargetTokens = 300;

    @Min(1)
    private int maxTargetTokens = 800;

    @Min(1)
    private int hardMaxTokens = 1000;

    @Min(0)
    private int overlapChars = 80;

    @Valid
    private SpringAi springAi = new SpringAi();

    /**
     * 软下限不能大于软上限。
     */
    @AssertTrue(message = "noterag.chunking.min-target-tokens must be less than or equal to max-target-tokens")
    public boolean isMinTargetTokensNotGreaterThanMaxTargetTokens() {
        return minTargetTokens <= maxTargetTokens;
    }

    /**
     * 硬上限不能小于软上限。
     */
    @AssertTrue(message = "noterag.chunking.hard-max-tokens must be greater than or equal to max-target-tokens")
    public boolean isHardMaxTokensNotLessThanMaxTargetTokens() {
        return hardMaxTokens >= maxTargetTokens;
    }

    /**
     * Spring AI 默认 TokenTextSplitter 的参数。
     *
     * <p>这些参数只在 `noterag.chunking.strategy=spring-ai-default` 时生效，用来做 baseline 对比，
     * 不参与 NoteRAG 自定义 Markdown chunk 规则。</p>
     */
    @Setter
    @Getter
    public static class SpringAi {

        @Min(1)
        private int chunkSize = 800;

        @Min(1)
        private int minChunkSizeChars = 350;

        @Min(0)
        private int minChunkLengthToEmbed = 5;

        @Min(1)
        private int maxNumChunks = 10000;

        private boolean keepSeparator = true;
    }
}
