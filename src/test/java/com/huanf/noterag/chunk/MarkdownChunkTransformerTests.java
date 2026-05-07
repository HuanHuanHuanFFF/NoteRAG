package com.huanf.noterag.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.huanf.noterag.config.ChunkingProperties;
import com.huanf.noterag.util.EstimatedTokenCounter;

class MarkdownChunkTransformerTests {

    private final ChunkingProperties chunkingProperties = defaultChunkingProperties();
    private final MarkdownChunkTransformer transformer =
            new MarkdownChunkTransformer(new MarkdownSectionParser(), new MarkdownChunker(chunkingProperties));

    @Test
    void transformKeepsHeadingPathMetadata() {
        Document source = new Document("""
                # Java

                Java notes.

                ## Collections

                HashMap notes.
                """);

        List<Document> chunks = transformer.transform(List.of(source));
        printChunks(chunks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Java")
                .containsEntry("chunkIndex", 0)
                .containsEntry("charCount", "Java notes.".length())
                .containsEntry("tokenCount", EstimatedTokenCounter.estimate("Java notes."));
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "Java > Collections")
                .containsEntry("chunkIndex", 1)
                .containsEntry("charCount", "HashMap notes.".length())
                .containsEntry("tokenCount", EstimatedTokenCounter.estimate("HashMap notes."));
    }

    @Test
    void transformRestartsChunkIndexForEachInputDocument() {
        Document first = new Document("""
                # Java

                Java notes.
                """);
        Document second = new Document("""
                # MySQL

                MySQL notes.
                """);

        List<Document> chunks = transformer.transform(List.of(first, second));
        printChunks(chunks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Java")
                .containsEntry("chunkIndex", 0)
                .containsEntry("tokenCount", EstimatedTokenCounter.estimate("Java notes."));
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "MySQL")
                .containsEntry("chunkIndex", 0)
                .containsEntry("tokenCount", EstimatedTokenCounter.estimate("MySQL notes."));
    }

    @Test
    void transformDoesNotEmitOverlapOnlyChunk() {
        Document source = new Document("""
                # Long Section

                %s

                %s
                """.formatted("中".repeat(790), "文".repeat(750)));

        List<Document> chunks = transformer.transform(List.of(source));
        printChunks(chunks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks)
                .extracting(Document::getText)
                .noneMatch(text -> text.length() == chunkingProperties.getOverlapChars());
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(chunk.getMetadata().get("tokenCount"))
                        .isInstanceOf(Integer.class));
        assertThat(chunks)
                .allSatisfy(chunk -> assertThat(chunk.getMetadata())
                        .containsEntry("headingPath", "Long Section"));
    }

    @Test
    void transformAllowsSoftMaxWhenCurrentChunkIsTooSmall() {
        Document source = new Document("""
                # Soft Limit

                %s

                %s
                """.formatted("中".repeat(180), "文".repeat(700)));

        List<Document> chunks = transformer.transform(List.of(source));
        printChunks(chunks);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).hasSize(882);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Soft Limit")
                .containsEntry("charCount", 882)
                .containsEntry("tokenCount", 880);
    }

    @Test
    void transformDoesNotOverSplitAsciiParagraphsByPerParagraphRounding() {
        String markdown = "# English\n\n" + String.join("\n\n", java.util.Collections.nCopies(1200, "a"));

        List<Document> chunks = transformer.transform(List.of(new Document(markdown)));
        printChunks(chunks);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "English")
                .containsEntry("tokenCount", 300);
    }

    @Test
    void transformSplitsLongParagraphByEstimatedHardTokenLimit() {
        Document source = new Document("""
                # Hard Limit

                %s
                """.formatted("中".repeat(1200)));

        List<Document> chunks = transformer.transform(List.of(source));
        printChunks(chunks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Hard Limit")
                .containsEntry("charCount", 1000)
                .containsEntry("tokenCount", chunkingProperties.getHardMaxTokens());
        assertThat(chunks.get(1).getText()).startsWith("中".repeat(chunkingProperties.getOverlapChars()));
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "Hard Limit")
                .containsEntry("tokenCount", 280);
    }

    @Test
    void transformKeepsOverlapOnCodePointBoundary() {
        String firstChunk = "中".repeat(977) + "😀".repeat(44) + "文";
        Document source = new Document("""
                # Unicode

                %s%s
                """.formatted(firstChunk, "尾".repeat(100)));

        List<Document> chunks = transformer.transform(List.of(source));
        printChunks(chunks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("tokenCount", chunkingProperties.getHardMaxTokens());
        assertThat(chunks.get(1).getText())
                .startsWith("中".repeat(35) + "😀".repeat(44) + "文");
    }

    @Test
    void transformIgnoresHeadingsInsideFencedCodeBlock() {
        Document source = new Document("""
                # Java

                Before code.

                ```java
                # Not A Heading
                class Demo {}
                ```

                After code.

                ## Real Heading

                Real content.
                """);

        List<Document> chunks = transformer.transform(List.of(source));
        printChunks(chunks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Java")
                .containsEntry("tokenCount", EstimatedTokenCounter.estimate(chunks.get(0).getText()));
        assertThat(chunks.get(0).getText()).contains("# Not A Heading");
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "Java > Real Heading")
                .containsEntry("tokenCount", EstimatedTokenCounter.estimate(chunks.get(1).getText()));
    }

    @Test
    void transformKeepsBlankLinesInsideFencedCodeBlockInSameChunk() {
        Document source = new Document("""
                # Java

                ```java
                class Demo {

                    void run() {
                    }
                }
                ```
                """);

        List<Document> chunks = transformer.transform(List.of(source));
        printChunks(chunks);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("tokenCount", EstimatedTokenCounter.estimate(chunks.get(0).getText()));
        assertThat(chunks.get(0).getText())
                .contains("```java\nclass Demo {\n\n    void run()")
                .contains("}\n```");
    }

    @Test
    void transformClosesFencedCodeBlockOnlyWithMatchingFenceType() {
        Document source = new Document("""
                # Java

                ~~~
                # Not A Heading
                ```
                ## Still Not A Heading
                ~~~

                ## Real Heading

                Real content.
                """);

        List<Document> chunks = transformer.transform(List.of(source));
        printChunks(chunks);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Java")
                .containsEntry("tokenCount", EstimatedTokenCounter.estimate(chunks.get(0).getText()));
        assertThat(chunks.get(0).getText())
                .contains("# Not A Heading")
                .contains("## Still Not A Heading");
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "Java > Real Heading")
                .containsEntry("tokenCount", EstimatedTokenCounter.estimate(chunks.get(1).getText()));
    }

    @Test
    void parserClosesFencedCodeBlockOnlyWithFenceLengthAtLeastOpeningLength() {
        MarkdownSectionParser parser = new MarkdownSectionParser();

        List<MarkdownSection> markdownSections = parser.parse("""
                # Java

                ````java
                # Not A Heading
                ```
                ## Still Not A Heading
                ````

                ## Real Heading

                Real content.
                """);
        printSections(markdownSections);

        assertThat(markdownSections).hasSize(2);
        assertThat(markdownSections.get(0).headingPath()).isEqualTo("Java");
        assertThat(markdownSections.get(0).paragraphs()).singleElement()
                .satisfies(paragraph -> assertThat(paragraph)
                        .contains("# Not A Heading")
                        .contains("## Still Not A Heading"));
        assertThat(markdownSections.get(1).headingPath()).isEqualTo("Java > Real Heading");
    }

    @Test
    void parserDoesNotCloseFencedCodeBlockWhenBackticksHaveTrailingText() {
        MarkdownSectionParser parser = new MarkdownSectionParser();

        List<MarkdownSection> markdownSections = parser.parse("""
                # Java

                ```java
                # Not A Heading
                ```oops
                ## Still Not A Heading
                ```

                ## Real Heading

                Real content.
                """);
        printSections(markdownSections);

        assertThat(markdownSections).hasSize(2);
        assertThat(markdownSections.get(0).headingPath()).isEqualTo("Java");
        assertThat(markdownSections.get(0).paragraphs()).singleElement()
                .satisfies(paragraph -> assertThat(paragraph)
                        .contains("# Not A Heading")
                        .contains("```oops")
                        .contains("## Still Not A Heading"));
        assertThat(markdownSections.get(1).headingPath()).isEqualTo("Java > Real Heading");
    }

    @Test
    void printTransformedChunksForInspection() {
        Document source = new Document("""
                # Java

                Java 是一门面向对象语言，常用于后端服务开发。

                ## Collections

                List、Set、Map 是集合框架中最常见的接口。HashMap 依赖 hash 定位桶位，
                再通过链表或红黑树处理冲突。

                ```java
                class Demo {

                    void run() {
                        System.out.println("hello");
                    }
                }
                ```

                ## JVM

                JVM 负责加载 class 文件、执行字节码，并提供内存管理和垃圾回收能力。
                """);

        List<Document> chunks = transformer.transform(List.of(source));
        printChunks(chunks);
        assertThat(chunks).isNotEmpty();
    }

    private void printChunks(List<Document> chunks) {
        for (Document chunk : chunks) {
            System.out.println("----- chunk -----");
            System.out.println("chunkIndex=" + chunk.getMetadata().get("chunkIndex"));
            System.out.println("headingPath=" + chunk.getMetadata().get("headingPath"));
            System.out.println("charCount=" + chunk.getMetadata().get("charCount"));
            System.out.println("tokenCount=" + chunk.getMetadata().get("tokenCount"));
            System.out.println(chunk.getText());
        }
    }

    private void printSections(List<MarkdownSection> markdownSections) {
        for (MarkdownSection markdownSection : markdownSections) {
            System.out.println("----- section -----");
            System.out.println("headingPath=" + markdownSection.headingPath());
            for (String paragraph : markdownSection.paragraphs()) {
                System.out.println("[paragraph]");
                System.out.println(paragraph);
            }
        }
    }

    private ChunkingProperties defaultChunkingProperties() {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setMinTargetTokens(300);
        properties.setMaxTargetTokens(800);
        properties.setHardMaxTokens(1000);
        properties.setOverlapChars(80);
        return properties;
    }
}
