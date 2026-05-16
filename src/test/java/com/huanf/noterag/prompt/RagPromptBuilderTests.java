package com.huanf.noterag.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huanf.noterag.model.RetrievedChunk;

class RagPromptBuilderTests {

    private final RagPromptBuilder builder = new RagPromptBuilder();

    @Test
    void systemPromptIncludesStrictRules() {
        RagPrompt prompt = builder.build("question", List.of(chunk("MySQL", "MySQL > 事务", "body")));

        assertThat(prompt.system())
                .contains("不得使用任何外部知识或网络信息")
                .contains("不得声称自己进行了联网搜索")
                .contains("根据当前笔记内容无法确定")
                .contains("使用与用户问题相同的语言")
                .contains("[1]");
    }

    @Test
    void userPromptIncludesNumberedSourcesAndQuestion() {
        List<RetrievedChunk> sources = List.of(
                chunk("MySQL", "MySQL > 事务", "first body"),
                chunk("MySQL", "MySQL > MVCC", "second body"));

        RagPrompt prompt = builder.build("MVCC是什么?", sources);

        String user = prompt.user();
        assertThat(user).contains("用户问题:\nMVCC是什么?");
        assertThat(user).contains("[1]");
        assertThat(user).contains("[2]");
        assertThat(user.indexOf("[1]")).isLessThan(user.indexOf("[2]"));
        assertThat(user).contains("文档标题: MySQL");
        assertThat(user).contains("章节路径: MySQL > 事务");
        assertThat(user).contains("章节路径: MySQL > MVCC");
        assertThat(user).contains("正文:\nfirst body");
        assertThat(user).contains("正文:\nsecond body");
    }

    @Test
    void userPromptOmitsHeadingLineWhenHeadingPathNull() {
        RagPrompt prompt = builder.build("question", List.of(chunk("MySQL", null, "body")));

        assertThat(prompt.user()).doesNotContain("章节路径:");
        assertThat(prompt.user()).doesNotContain("null");
    }

    @Test
    void userPromptOmitsHeadingLineWhenHeadingPathBlank() {
        RagPrompt prompt = builder.build("question", List.of(chunk("MySQL", "   ", "body")));

        assertThat(prompt.user()).doesNotContain("章节路径:");
    }

    @Test
    void userPromptNormalizesQuestion() {
        RagPrompt prompt = builder.build("  hello  ", List.of(chunk("t", "h", "c")));

        assertThat(prompt.user()).contains("用户问题:\nhello\n");
        assertThat(prompt.user()).doesNotContain("  hello");
    }

    @Test
    void userPromptAddsNoticeWhenSourcesEmpty() {
        RagPrompt prompt = builder.build("question", List.of());

        assertThat(prompt.user()).contains("用户问题:\nquestion");
        assertThat(prompt.user()).contains("（未检索到相关笔记片段）");
        assertThat(prompt.user()).doesNotContain("[1]");
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

    private RetrievedChunk chunk(String title, String headingPath, String content) {
        return new RetrievedChunk(1L, 1L, title, headingPath, content, 0.9);
    }
}
