package com.huanf.noterag.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.rag.CitationMarkers;
import com.huanf.noterag.rag.RagPrompt;
import com.huanf.noterag.rag.RagPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagPromptBuilderTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RagPromptBuilder builder = new RagPromptBuilder();

    @Test
    void systemPromptIncludesCitationRulesAndAllowsMarkdown() {
        RagPrompt prompt = builder.build("question", List.of(chunk(140L, "MySQL", "MySQL > Tx", "body")));

        assertThat(prompt.system())
                .contains(CitationMarkers.formatPlaceholder())
                .contains(CitationMarkers.format(140L))
                .contains(CitationMarkers.format(141L))
                .contains("sourceId")
                .contains("Markdown")
                .contains("代码块")
                .contains("相关性从高到低")
                .contains("片段互相矛盾")
                .contains("片段有限信息")
                .contains("片段都无关")
                .contains("未检索到相关笔记片段")
                .contains("不加空格")
                .contains("参考来源")
                .contains("前端会负责渲染")
                .contains("严禁写进代码块内部")
                .doesNotContain("[1]")
                .doesNotContain("s1")
                .doesNotContain("不要输出 Markdown 代码块");
    }

    @Test
    void userPromptIncludesChunkIdSourcesAndQuestion() {
        List<RetrievedChunk> sources = List.of(
                chunk(140L, "MySQL", "MySQL > Tx", "first body"),
                chunk(218L, "MySQL", "MySQL > MVCC", "second body"));

        RagPrompt prompt = builder.build(" what is MVCC? ", sources);

        String user = prompt.user();
        assertThat(user).contains("what is MVCC?");
        assertThat(user).contains("笔记片段（按与问题的相关性从高到低排列）:");
        assertThat(user).contains("sourceId: 140");
        assertThat(user).contains("sourceId: 218");
        assertThat(user).doesNotContain("sourceId: 1\n");
        assertThat(user).doesNotContain("sourceId: 2\n");
        assertThat(user.indexOf("sourceId: 140")).isLessThan(user.indexOf("sourceId: 218"));
        assertThat(user).contains("MySQL > Tx");
        assertThat(user).contains("MySQL > MVCC");
        assertThat(user).contains("first body");
        assertThat(user).contains("second body");
    }

    @Test
    void printsBuiltPromptJsonWithSourcesForReview() throws JsonProcessingException {
        List<RetrievedChunk> sources = List.of(
                chunk(140L, "MySQL interview notes", "MySQL > Tx > MVCC", "MVCC depends on ReadView and undo log."),
                chunk(305L, "MySQL interview notes", "MySQL > Locks", "Next-Key Lock combines record and gap locks."));

        RagPrompt prompt = builder.build("What does MVCC depend on?", sources);

        System.out.println("----- rag prompt json -----");
        System.out.println(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(prompt));
        System.out.println("----- rag prompt json end -----");

        assertThat(prompt.system()).contains(CitationMarkers.formatPlaceholder());
        assertThat(prompt.user()).contains("What does MVCC depend on?");
        assertThat(prompt.user()).contains("sourceId: 140");
        assertThat(prompt.user()).contains("sourceId: 305");
    }

    @Test
    void userPromptOmitsHeadingLineWhenHeadingPathNull() {
        RagPrompt prompt = builder.build("question", List.of(chunk(140L, "MySQL", null, "body")));

        assertThat(prompt.user()).doesNotContain("null");
    }

    @Test
    void userPromptOmitsHeadingLineWhenHeadingPathBlank() {
        RagPrompt prompt = builder.build("question", List.of(chunk(140L, "MySQL", "   ", "body")));

        assertThat(prompt.user()).doesNotContain("   ");
    }

    @Test
    void userPromptNormalizesQuestion() {
        RagPrompt prompt = builder.build("  hello  ", List.of(chunk(140L, "t", "h", "c")));

        assertThat(prompt.user()).contains("hello");
        assertThat(prompt.user()).doesNotContain("  hello");
    }

    @Test
    void userPromptAddsNoticeWhenSourcesEmpty() {
        RagPrompt prompt = builder.build("question", List.of());

        assertThat(prompt.user()).contains("question");
        assertThat(prompt.user()).contains("笔记片段（按与问题的相关性从高到低排列）:");
        assertThat(prompt.user()).contains("（未检索到相关笔记片段）");
        assertThat(prompt.user()).doesNotContain("sourceId:");
    }

    @Test
    void buildThrowsWhenQuestionNull() {
        assertThatThrownBy(() -> builder.build(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildThrowsWhenQuestionBlank() {
        assertThatThrownBy(() -> builder.build("   ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildThrowsWhenSourcesNull() {
        assertThatThrownBy(() -> builder.build("question", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildThrowsWhenChunkIdNull() {
        RetrievedChunk chunk = new RetrievedChunk(1L, null, "title", "heading", "content", 0.9);

        assertThatThrownBy(() -> builder.build("question", List.of(chunk)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCodeStatus()).isEqualTo(CodeStatus.INTERNAL_ERROR));
    }

    private RetrievedChunk chunk(Long chunkId, String title, String headingPath, String content) {
        return new RetrievedChunk(1L, chunkId, title, headingPath, content, 0.9);
    }
}
