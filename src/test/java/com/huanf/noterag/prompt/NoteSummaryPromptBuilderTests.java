package com.huanf.noterag.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.huanf.noterag.rag.NoteSummaryPromptBuilder;
import com.huanf.noterag.rag.RagPrompt;

class NoteSummaryPromptBuilderTests {

    private final NoteSummaryPromptBuilder builder = new NoteSummaryPromptBuilder();

    @Test
    void buildCreatesPlainSummaryPrompt() {
        RagPrompt prompt = builder.build("  MySQL  ", "# MySQL\n\nMVCC notes.");

        assertThat(prompt.system())
                .contains("全文级技术摘要")
                .contains("不引入外部知识")
                .contains("只输出摘要正文")
                .doesNotContain("JSON")
                .doesNotContain("citation marker")
                .doesNotContain("sourceId");
        assertThat(prompt.user())
                .contains("<note_title>\nMySQL\n</note_title>")
                .contains("<note_markdown>\n# MySQL\n\nMVCC notes.\n</note_markdown>");
    }

    @Test
    void buildRejectsBlankTitleOrContent() {
        assertThatThrownBy(() -> builder.build(" ", "# MySQL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title must not be null or blank");

        assertThatThrownBy(() -> builder.build("MySQL", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content must not be null or blank");
    }
}
