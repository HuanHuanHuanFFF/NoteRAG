package com.huanf.noterag.util;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;

/**
 * 持久化和展示用 token_count 统计器。
 *
 * <p>用于写入 notes/note_chunks 的 token_count、接口返回和 metadata 展示，
 * 固定使用通用 CL100K_BASE 编码。它不负责 Markdown chunk 边界估算；
 * chunk 合并、截断和 overlap 仍由 {@link EstimatedTokenCounter} 的轻量估算规则控制。</p>
 */
public final class TokenCounter {

    private static final TokenCountEstimator TOKEN_COUNT_ESTIMATOR =
            new JTokkitTokenCountEstimator(EncodingType.CL100K_BASE);

    private TokenCounter() {
    }

    /**
     * 返回 CL100K_BASE token 数；空文本按 0 处理。
     */
    public static int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return TOKEN_COUNT_ESTIMATOR.estimate(text);
    }
}
