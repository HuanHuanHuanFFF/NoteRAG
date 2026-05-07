package com.huanf.noterag.chunk;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

/**
 * Spring AI 的 Markdown chunk 适配层。
 *
 * <p>本类只负责接入 DocumentTransformer：过滤空文档、调用 MarkdownSectionParser 解析 section、
 * 调用 MarkdownChunker 生成 chunk。Markdown 解析、token 规则和 overlap 逻辑都不放在这里。</p>
 */
@Component
public class MarkdownChunkTransformer implements DocumentTransformer {

    public static final String DOCUMENT_ID_METADATA_KEY = "documentId";
    public static final String CHUNK_INDEX_METADATA_KEY = "chunkIndex";
    public static final String HEADING_PATH_METADATA_KEY = "headingPath";
    public static final String CHAR_COUNT_METADATA_KEY = "charCount";
    public static final String TOKEN_COUNT_METADATA_KEY = "tokenCount";

    private final MarkdownSectionParser parser;
    private final MarkdownChunker chunker;

    /**
     * Spring 构造器注入入口。Transformer 只编排 parser 和 chunker，不直接实现解析或切块规则。
     */
    MarkdownChunkTransformer(MarkdownSectionParser parser, MarkdownChunker chunker) {
        this.parser = parser;
        this.chunker = chunker;
    }

    /**
     * Spring AI DocumentTransformer 的标准入口，保持与 apply 行为一致。
     */
    @Override
    public List<Document> transform(List<Document> documents) {
        return apply(documents);
    }

    /**
     * 将输入文档逐篇转换成 chunk；chunkIndex 在每篇文档内从 0 开始。
     *
     * <p>调用方必须在 source metadata 中传入已持久化的 {@value #DOCUMENT_ID_METADATA_KEY}。
     * Transformer 会把该标识原样复制到每个 chunk metadata，供后续入库、批处理或异步 chunk
     * 流程回收来源 document 归属。</p>
     */
    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<Document> chunks = new ArrayList<>();
        for (Document document : documents) {
            if (document == null || document.getText() == null || document.getText().isBlank()) {
                continue;
            }
            validateRequiredMetadata(document);

            List<MarkdownSection> sections = parser.parse(document.getText());
            List<Document> chunked = chunker.chunk(document.getMetadata(), sections);
            chunks.addAll(chunked);
        }

        return chunks;
    }

    private void validateRequiredMetadata(Document document) {
        if (document.getMetadata() == null || !document.getMetadata().containsKey(DOCUMENT_ID_METADATA_KEY)) {
            throw new IllegalArgumentException("source metadata must contain documentId");
        }
    }
}
