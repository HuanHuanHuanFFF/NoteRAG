package com.huanf.noterag.chunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.huanf.noterag.config.ChunkingProperties;
import com.huanf.noterag.util.EstimatedTokenCounter;
import com.huanf.noterag.util.EstimatedTokenCounter.TokenCounts;

/**
 * 将 Markdown section 组装为 Spring AI chunk Document。
 *
 * <p>本类只负责 chunk 规则：token 软/硬上限、段落合并、overlap、chunkIndex、
 * `headingPath`、`charCount` 和 `tokenCount` metadata。不负责 Markdown 解析或业务入库。</p>
 */
@Component
final class MarkdownChunker {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private final ChunkingProperties chunkingProperties;

    /**
     * 注入 chunk 规则配置，便于通过配置文件调参。
     */
    MarkdownChunker(ChunkingProperties chunkingProperties) {
        this.chunkingProperties = chunkingProperties;
    }

    /**
     * 将同一篇源文档的 sections 转为 chunks；chunkIndex 从 0 开始递增。
     */
    List<Document> chunk(Map<String, Object> sourceMetadata, List<MarkdownSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }

        List<Document> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (MarkdownSection markdownSection : sections) {
            chunkIndex = appendSectionChunks(sourceMetadata, markdownSection, chunkIndex, chunks);
        }
        return chunks;
    }

    /**
     * 在单个 headingPath 内合并段落并输出 chunk；overlap 不跨 section。
     */
    private int appendSectionChunks(
            Map<String, Object> sourceMetadata,
            MarkdownSection markdownSection,
            int chunkIndex,
            List<Document> chunks) {
        StringBuilder current = new StringBuilder();
        TokenCounts currentTokenCounts = TokenCounts.EMPTY;
        boolean hasBodyText = false;

        for (String paragraph : markdownSection.paragraphs()) {
            String remaining = paragraph.strip();
            if (remaining.isEmpty()) {
                continue;
            }
            TokenCounts remainingTokenCounts = EstimatedTokenCounter.count(remaining);

            if (hasBodyText && shouldEmitBeforeAppend(currentTokenCounts, remainingTokenCounts)) {
                EmitResult result = emitChunk(sourceMetadata, markdownSection.headingPath(), current, chunkIndex, chunks, true);
                chunkIndex = result.nextChunkIndex();
                currentTokenCounts = result.currentTokenCounts();
                hasBodyText = false;
            }

            while (!remaining.isEmpty()) {
                int currentTokenCount = currentTokenCounts.estimate();
                int availableTokens = currentChunkLimit(currentTokenCount) - currentTokenCount;

                if (availableTokens <= 0) {
                    EmitResult result = emitChunk(sourceMetadata, markdownSection.headingPath(), current, chunkIndex, chunks, true);
                    chunkIndex = result.nextChunkIndex();
                    currentTokenCounts = result.currentTokenCounts();
                    hasBodyText = false;
                    continue;
                }

                if (remainingTokenCounts.estimate() <= availableTokens) {
                    appendParagraph(current, remaining);
                    currentTokenCounts = currentTokenCounts.plus(remainingTokenCounts);
                    hasBodyText = true;
                    remaining = "";
                    remainingTokenCounts = TokenCounts.EMPTY;
                } else {
                    String piece = takeByEstimatedTokens(remaining, availableTokens).stripTrailing();
                    if (piece.isEmpty()) {
                        piece = takeByEstimatedTokens(remaining, availableTokens);
                    }
                    appendParagraph(current, piece);
                    EmitResult result = emitChunk(sourceMetadata, markdownSection.headingPath(), current, chunkIndex, chunks, true);
                    chunkIndex = result.nextChunkIndex();
                    currentTokenCounts = result.currentTokenCounts();
                    hasBodyText = false;
                    remaining = remaining.substring(piece.length()).stripLeading();
                    remainingTokenCounts = EstimatedTokenCounter.count(remaining);
                }
            }
        }

        if (hasBodyText && !current.isEmpty()) {
            EmitResult result = emitChunk(sourceMetadata, markdownSection.headingPath(), current, chunkIndex, chunks, false);
            chunkIndex = result.nextChunkIndex();
        }

        return chunkIndex;
    }

    /**
     * 判断追加段落前是否应该先输出当前 chunk。800 tokens 是软上限，1000 tokens 是硬上限。
     */
    private boolean shouldEmitBeforeAppend(TokenCounts currentTokenCounts, TokenCounts paragraphTokenCounts) {
        int currentTokenCount = currentTokenCounts.estimate();
        if (currentTokenCount == 0) {
            return paragraphTokenCounts.estimate() > chunkingProperties.getHardMaxTokens();
        }

        int candidateTokenCount = currentTokenCounts.plus(paragraphTokenCounts).estimate();
        if (candidateTokenCount <= chunkingProperties.getMaxTargetTokens()) {
            return false;
        }
        return currentTokenCount >= chunkingProperties.getMinTargetTokens()
                || candidateTokenCount > chunkingProperties.getHardMaxTokens();
    }

    /**
     * 当前 chunk 未达到软下限时允许接近硬上限，减少过小碎片。
     */
    private int currentChunkLimit(int currentTokenCount) {
        return currentTokenCount < chunkingProperties.getMinTargetTokens()
                ? chunkingProperties.getHardMaxTokens()
                : chunkingProperties.getMaxTargetTokens();
    }

    /**
     * 输出当前缓冲区为 chunk Document，并按需把右侧 overlap 写回缓冲区。
     */
    private EmitResult emitChunk(
            Map<String, Object> sourceMetadata,
            String headingPath,
            StringBuilder current,
            int chunkIndex,
            List<Document> chunks,
            boolean keepOverlap) {
        String chunkText = current.toString().strip();
        if (chunkText.isEmpty()) {
            current.setLength(0);
            return new EmitResult(chunkIndex, TokenCounts.EMPTY);
        }

        TokenCounts chunkTokenCounts = EstimatedTokenCounter.count(chunkText);
        int tokenCount = chunkTokenCounts.estimate();
        chunks.add(new Document(chunkText, buildMetadata(
                sourceMetadata,
                headingPath,
                chunkIndex,
                chunkText.length(),
                tokenCount)));
        current.setLength(0);

        TokenCounts currentTokenCounts = TokenCounts.EMPTY;
        if (keepOverlap) {
            String overlap = rightOverlap(chunkText);
            current.append(overlap);
            currentTokenCounts = EstimatedTokenCounter.count(overlap);
        }

        return new EmitResult(chunkIndex + 1, currentTokenCounts);
    }

    /**
     * 复制源 metadata，并追加 chunk 自身的 headingPath、索引和长度信息。
     */
    private Map<String, Object> buildMetadata(
            Map<String, Object> sourceMetadata,
            String headingPath,
            int chunkIndex,
            int charCount,
            int tokenCount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (sourceMetadata != null) {
            metadata.putAll(sourceMetadata);
        }
        metadata.put(MarkdownChunkTransformer.HEADING_PATH_METADATA_KEY, headingPath);
        metadata.put(MarkdownChunkTransformer.CHUNK_INDEX_METADATA_KEY, chunkIndex);
        metadata.put(MarkdownChunkTransformer.CHAR_COUNT_METADATA_KEY, charCount);
        metadata.put(MarkdownChunkTransformer.TOKEN_COUNT_METADATA_KEY, tokenCount);
        return metadata;
    }

    /**
     * 将段落追加到当前 chunk 缓冲区，段落之间保留 Markdown 空行。
     */
    private void appendParagraph(StringBuilder current, String paragraph) {
        if (!current.isEmpty()) {
            current.append(PARAGRAPH_SEPARATOR);
        }
        current.append(paragraph);
    }

    /**
     * 从长段落左侧截取估算 token 不超过上限的前缀。
     *
     * <p>这里是逐字符扫描的高频路径，只维护局部分类计数，不逐字符创建 TokenCounts。</p>
     */
    private String takeByEstimatedTokens(String text, int maxTokens) {
        if (maxTokens <= 0 || text == null || text.isBlank()) {
            return "";
        }

        int cjkCount = 0;
        int asciiCount = 0;
        int otherCount = 0;
        int end = 0;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int next = i + Character.charCount(codePoint);

            int nextCjkCount = cjkCount;
            int nextAsciiCount = asciiCount;
            int nextOtherCount = otherCount;
            switch (EstimatedTokenCounter.classify(codePoint)) {
                case CJK -> nextCjkCount++;
                case ASCII -> nextAsciiCount++;
                case OTHER -> nextOtherCount++;
                case IGNORED -> {
                }
            }

            if (EstimatedTokenCounter.estimate(nextCjkCount, nextAsciiCount, nextOtherCount) > maxTokens) {
                break;
            }

            cjkCount = nextCjkCount;
            asciiCount = nextAsciiCount;
            otherCount = nextOtherCount;
            end = next;
            i = next;
        }

        return text.substring(0, end);
    }

    /**
     * 取 chunk 右侧固定 code point 数作为 overlap，避免切断 emoji 等非 BMP 字符。
     */
    private String rightOverlap(String text) {
        int codePointCount = text.codePointCount(0, text.length());
        if (codePointCount <= chunkingProperties.getOverlapChars()) {
            return text;
        }
        int beginIndex = text.offsetByCodePoints(text.length(), -chunkingProperties.getOverlapChars());
        return text.substring(beginIndex);
    }

    /**
     * emitChunk 后返回下一次写入所需的 chunkIndex 和 overlap token 计数。
     */
    private record EmitResult(int nextChunkIndex, TokenCounts currentTokenCounts) {
    }
}
