package com.huanf.noterag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.huanf.noterag.client.LlmClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.LlmProperties;
import com.huanf.noterag.rag.NoteSummaryPromptBuilder;

/**
 * Note 全文摘要生成服务。
 *
 * <p>摘要只用于生成 SUMMARY chunk 参与检索召回，不承担问答、引用或来源编号职责。</p>
 */
@Slf4j
@Service
public class NoteSummaryService {

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final NoteSummaryPromptBuilder noteSummaryPromptBuilder;

    public NoteSummaryService(
            LlmClient llmClient,
            LlmProperties llmProperties,
            NoteSummaryPromptBuilder noteSummaryPromptBuilder
    ) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.noteSummaryPromptBuilder = noteSummaryPromptBuilder;
    }

    /**
     * 调用 LLM 生成全文摘要；LLM 未启用或摘要为空时导入失败。
     */
    public String generateSummary(String title, String content) {
        validateLlmEnabled();
        long startNanos = System.nanoTime();
        log.info("Note summary 生成开始, titleLength={}, contentLength={}",
                length(title), length(content));

        String answer = llmClient.chat(noteSummaryPromptBuilder.build(title, content));
        String summary = answer == null ? "" : answer.strip();
        if (summary.isEmpty()) {
            throw new BusinessException(CodeStatus.LLM_RESULT_INVALID, "Note summary must not be blank");
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("Note summary 生成完成, summaryLength={}, elapsedMs={}", summary.length(), elapsedMs);
        return summary;
    }

    /**
     * 导入 summary chunk 依赖真实 LLM，禁用 LLM 时不能落入 no-op 空摘要。
     */
    private void validateLlmEnabled() {
        if (!llmProperties.isEnabled()) {
            throw new BusinessException(CodeStatus.LLM_CONFIG_INVALID, "LLM is disabled for note summary");
        }
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }
}
