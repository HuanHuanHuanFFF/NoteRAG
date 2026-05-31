package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.huanf.noterag.client.LlmClient;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.LlmProperties;
import com.huanf.noterag.rag.NoteSummaryPromptBuilder;
import com.huanf.noterag.rag.RagPrompt;

class NoteSummaryServiceTests {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final LlmProperties llmProperties = enabledLlmProperties();
    private final NoteSummaryPromptBuilder noteSummaryPromptBuilder = new NoteSummaryPromptBuilder();
    private final NoteSummaryService noteSummaryService = new NoteSummaryService(
            llmClient,
            llmProperties,
            noteSummaryPromptBuilder);

    @Test
    void generateSummaryBuildsMarkdownSummaryPromptAndReturnsStrippedText() {
        when(llmClient.chat(any())).thenReturn("  MySQL 事务摘要  ");

        String summary = noteSummaryService.generateSummary("MySQL", "# MySQL\n\nMVCC notes.");

        assertThat(summary).isEqualTo("MySQL 事务摘要");
        ArgumentCaptor<RagPrompt> promptCaptor = ArgumentCaptor.forClass(RagPrompt.class);
        verify(llmClient).chat(promptCaptor.capture());
        RagPrompt prompt = promptCaptor.getValue();
        assertThat(prompt.system())
                .contains("全文级摘要")
                .contains("输出 Markdown 格式")
                .contains("只输出摘要正文");
        assertThat(prompt.user())
                .contains("### 概览")
                .contains("### 核心内容")
                .contains("<note_title>")
                .contains("MySQL")
                .contains("<note_markdown>")
                .contains("# MySQL\n\nMVCC notes.");
    }

    @Test
    void generateSummaryFailsWhenLlmDisabled() {
        llmProperties.setEnabled(false);

        assertThatThrownBy(() -> noteSummaryService.generateSummary("MySQL", "# MySQL"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.LLM_CONFIG_INVALID));
        verifyNoInteractions(llmClient);
    }

    @Test
    void generateSummaryFailsWhenLlmReturnsBlankSummary() {
        when(llmClient.chat(any())).thenReturn("   ");

        assertThatThrownBy(() -> noteSummaryService.generateSummary("MySQL", "# MySQL"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.LLM_RESULT_INVALID));
    }

    private static LlmProperties enabledLlmProperties() {
        LlmProperties properties = new LlmProperties();
        properties.setEnabled(true);
        return properties;
    }
}
