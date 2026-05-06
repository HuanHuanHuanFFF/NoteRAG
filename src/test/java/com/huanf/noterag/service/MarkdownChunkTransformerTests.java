package com.huanf.noterag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class MarkdownChunkTransformerTests {

    private final MarkdownChunkTransformer transformer = new MarkdownChunkTransformer();

    @Test
    void transformKeepsHeadingPathMetadata() {
        Document source = new Document("""
                # Java

                Java notes.

                ## Collections

                HashMap notes.
                """);

        List<Document> chunks = transformer.transform(List.of(source));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Java")
                .containsEntry("chunkIndex", 0)
                .containsEntry("charCount", "Java notes.".length());
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "Java > Collections")
                .containsEntry("chunkIndex", 1)
                .containsEntry("charCount", "HashMap notes.".length());
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

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Java")
                .containsEntry("chunkIndex", 0);
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "MySQL")
                .containsEntry("chunkIndex", 0);
    }

    @Test
    void transformDoesNotEmitOverlapOnlyChunk() {
        Document source = new Document("""
                # Long Section

                %s

                %s
                """.formatted("a".repeat(790), "b".repeat(750)));

        List<Document> chunks = transformer.transform(List.of(source));

        assertThat(chunks).hasSize(2);
        assertThat(chunks)
                .extracting(Document::getText)
                .noneMatch(text -> text.length() == MarkdownChunkTransformer.OVERLAP_CHARS);
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
                """.formatted("a".repeat(180), "b".repeat(700)));

        List<Document> chunks = transformer.transform(List.of(source));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).hasSize(882);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Soft Limit")
                .containsEntry("charCount", 882);
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

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Java");
        assertThat(chunks.get(0).getText()).contains("# Not A Heading");
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "Java > Real Heading");
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

        assertThat(chunks).hasSize(1);
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

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry("headingPath", "Java");
        assertThat(chunks.get(0).getText())
                .contains("# Not A Heading")
                .contains("## Still Not A Heading");
        assertThat(chunks.get(1).getMetadata())
                .containsEntry("headingPath", "Java > Real Heading");
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

        assertThat(chunks).isNotEmpty();
        for (Document chunk : chunks) {
            System.out.println("----- chunk -----");
            System.out.println("chunkIndex=" + chunk.getMetadata().get("chunkIndex"));
            System.out.println("headingPath=" + chunk.getMetadata().get("headingPath"));
            System.out.println("charCount=" + chunk.getMetadata().get("charCount"));
            System.out.println(chunk.getText());
        }
    }
}
