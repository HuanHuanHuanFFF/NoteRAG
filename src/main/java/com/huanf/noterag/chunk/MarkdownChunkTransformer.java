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

            List<MarkdownSection> sections = parser.parse(document.getText());
            List<Document> chunked = chunker.chunk(document.getMetadata(), sections);
            chunks.addAll(chunked);
        }

        return chunks;
    }
}
