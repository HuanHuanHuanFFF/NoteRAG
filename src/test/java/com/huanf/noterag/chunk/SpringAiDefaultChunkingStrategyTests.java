package com.huanf.noterag.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class SpringAiDefaultChunkingStrategyTests {

    private final SpringAiDefaultChunkingStrategy strategy = new SpringAiDefaultChunkingStrategy();

    @Test
    void transformAddsProjectMetadataWithoutHeadingPath() {
        Long documentId = 42L;
        String content = "# Java\n\n" + "Spring AI default baseline chunk text. ".repeat(1200);

        List<Document> chunks = strategy.transform(List.of(new Document(
                content,
                Map.of(MarkdownChunkTransformer.DOCUMENT_ID_METADATA_KEY, documentId))));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks)
                .extracting(chunk -> chunk.getMetadata().get(MarkdownChunkTransformer.CHUNK_INDEX_METADATA_KEY))
                .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getText()).isNotBlank();
            assertThat(chunk.getMetadata())
                    .containsEntry(MarkdownChunkTransformer.DOCUMENT_ID_METADATA_KEY, documentId)
                    .containsKey(MarkdownChunkTransformer.CHAR_COUNT_METADATA_KEY)
                    .containsKey(MarkdownChunkTransformer.TOKEN_COUNT_METADATA_KEY);
            assertThat((Integer) chunk.getMetadata().get(MarkdownChunkTransformer.CHAR_COUNT_METADATA_KEY))
                    .isGreaterThan(0);
            assertThat((Integer) chunk.getMetadata().get(MarkdownChunkTransformer.TOKEN_COUNT_METADATA_KEY))
                    .isGreaterThan(0);
            assertThat(chunk.getMetadata().get(MarkdownChunkTransformer.HEADING_PATH_METADATA_KEY)).isNull();
        });
    }
}
