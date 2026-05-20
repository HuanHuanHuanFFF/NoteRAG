package com.huanf.noterag.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.entity.ChatMessage;
import com.huanf.noterag.entity.ChatMessageRole;
import com.huanf.noterag.entity.ChatMessageStatus;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.rag.ChatPromptBuilder;
import com.huanf.noterag.rag.CitationMarkers;
import com.huanf.noterag.rag.RagPrompt;

class ChatPromptBuilderTests {

    private final ChatPromptBuilder builder = new ChatPromptBuilder();

    @Test
    void buildIncludesHistoryQuestionAndSourceIds() {
        List<ChatMessage> history = List.of(
                message(1L, ChatMessageRole.USER, "什么是 MVCC?"),
                message(2L, ChatMessageRole.ASSISTANT, "旧回答" + CitationMarkers.format(140L)));
        List<RetrievedChunk> sources = List.of(
                chunk(10L, 140L, "MySQL", "Tx > MVCC", "undo log"),
                chunk(10L, 141L, "MySQL", "Tx > MVCC", "read view"));

        RagPrompt prompt = builder.build(history, "那它依赖什么?", sources);

        assertThat(prompt.system()).contains(CitationMarkers.formatPlaceholder());
        assertThat(prompt.user()).contains("历史对话:");
        assertThat(prompt.user()).contains("用户:\n什么是 MVCC?");
        assertThat(prompt.user()).contains("助手:\n旧回答");
        assertThat(prompt.user()).doesNotContain(CitationMarkers.format(140L));
        assertThat(prompt.user()).contains("当前问题:\n那它依赖什么?");
        assertThat(prompt.user()).contains("sourceId: 140");
        assertThat(prompt.user()).contains("sourceId: 141");
    }

    @Test
    void buildShowsNoHistoryAndNoSourcesNotice() {
        RagPrompt prompt = builder.build(List.of(), "问题", List.of());

        assertThat(prompt.user()).contains("（无历史对话）");
        assertThat(prompt.user()).contains("（未检索到相关笔记片段）");
    }

    @Test
    void buildRejectsBlankQuestion() {
        assertThatThrownBy(() -> builder.build(List.of(), " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildRejectsNullHistoryMessagesAsInternalContractViolation() {
        assertThatThrownBy(() -> builder.build(null, "question", List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("historyMessages must not be null");
    }

    @Test
    void buildRejectsNullSourcesAsInternalContractViolation() {
        assertThatThrownBy(() -> builder.build(List.of(), "question", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sources must not be null");
    }

    @Test
    void buildRejectsNullChunkId() {
        RetrievedChunk source = new RetrievedChunk(1L, null, "title", "heading", "content", 0.9);

        assertThatThrownBy(() -> builder.build(List.of(), "question", List.of(source)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.INTERNAL_ERROR));
    }

    private static ChatMessage message(Long id, ChatMessageRole role, String content) {
        return new ChatMessage(id, 1L, role, content, ChatMessageStatus.COMPLETED, null, content.length(), null, null);
    }

    private static RetrievedChunk chunk(Long noteId, Long chunkId, String title, String headingPath, String content) {
        return new RetrievedChunk(noteId, chunkId, title, headingPath, content, 0.9);
    }
}
