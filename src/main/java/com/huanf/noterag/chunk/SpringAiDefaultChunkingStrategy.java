package com.huanf.noterag.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.huanf.noterag.config.ChunkingProperties;
import com.huanf.noterag.util.EstimatedTokenCounter;

/**
 * Spring AI 默认 TokenTextSplitter baseline。
 *
 * <p>本策略只补齐 NoteRAG 入库所需的工程 metadata，不解析 Markdown 标题，也不生成 headingPath，
 * 避免 baseline 使用自定义 Markdown chunk 方案的优势。</p>
 */
@Component
@ConditionalOnProperty(name = "noterag.chunking.strategy", havingValue = "spring-ai-default")
public class SpringAiDefaultChunkingStrategy implements ChunkingStrategy {

    private final TokenTextSplitter tokenTextSplitter;

    public SpringAiDefaultChunkingStrategy(ChunkingProperties chunkingProperties) {
        ChunkingProperties.SpringAi springAi = chunkingProperties.getSpringAi();
        this.tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(springAi.getChunkSize())
                .withMinChunkSizeChars(springAi.getMinChunkSizeChars())
                .withMinChunkLengthToEmbed(springAi.getMinChunkLengthToEmbed())
                .withMaxNumChunks(springAi.getMaxNumChunks())
                .withKeepSeparator(springAi.isKeepSeparator())
                .build();
    }

    @Override
    public List<Document> transform(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<Document> chunks = new ArrayList<>();
        for (Document document : documents) {
            if (document == null || document.getText() == null || document.getText().isBlank()) {
                continue;
            }
            Object documentId = readRequiredDocumentId(document);
            List<Document> splitChunks = tokenTextSplitter.split(document);
            for (int chunkIndex = 0; chunkIndex < splitChunks.size(); chunkIndex++) {
                Document splitChunk = splitChunks.get(chunkIndex);
                String text = splitChunk.getText();
                Map<String, Object> metadata = new HashMap<>(splitChunk.getMetadata());
                metadata.put(MarkdownChunkTransformer.DOCUMENT_ID_METADATA_KEY, documentId);
                metadata.put(MarkdownChunkTransformer.CHUNK_INDEX_METADATA_KEY, chunkIndex);
                metadata.put(MarkdownChunkTransformer.CHAR_COUNT_METADATA_KEY, text.length());
                metadata.put(MarkdownChunkTransformer.TOKEN_COUNT_METADATA_KEY, EstimatedTokenCounter.estimate(text));
                metadata.remove(MarkdownChunkTransformer.HEADING_PATH_METADATA_KEY);
                chunks.add(new Document(text, metadata));
            }
        }
        return chunks;
    }

    private Object readRequiredDocumentId(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null || !metadata.containsKey(MarkdownChunkTransformer.DOCUMENT_ID_METADATA_KEY)) {
            throw new IllegalArgumentException("source metadata must contain documentId");
        }
        return metadata.get(MarkdownChunkTransformer.DOCUMENT_ID_METADATA_KEY);
    }
}
