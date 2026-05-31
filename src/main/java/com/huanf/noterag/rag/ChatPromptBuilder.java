package com.huanf.noterag.rag;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.entity.ChatMessage;
import com.huanf.noterag.entity.ChatMessageRole;
import com.huanf.noterag.model.RetrievedChunk;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ChatPromptBuilder {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是 NoteRAG 的多轮笔记问答助手。请结合历史对话理解用户追问，并只根据本轮候选笔记片段回答。
            
            要求：
            1. 历史对话只用于理解指代，不作为事实来源。
            2. 可以概括、合并和解释片段内容，让回答自然、清楚，不要像搜索结果摘要。
            3. 不要补充候选片段没有支持的外部事实；片段不足以回答时，只回答：根据当前笔记内容无法确定。
            4. 不要说“根据候选片段”“参考来源如下”等内部说明。
            
            引用：
            1. 使用片段信息时，在对应段落、列表项或关键事实点末尾添加 citation marker。
            2. marker 格式：{{citationMarkerFormat}}，只能引用候选 sourceId。
            3. 多个 marker 连续书写，例如：MySQL 的 MVCC 依赖隐藏字段、Read View 和 undo log。{{citationExample}}
            4. citation marker 不要写进代码块。
            """;

    private static final String SYSTEM_PROMPT = SYSTEM_PROMPT_TEMPLATE
            .replace("{{citationMarkerFormat}}", CitationMarkers.formatPlaceholder())
            .replace("{{citationExample}}", CitationMarkers.format(140L) + CitationMarkers.format(141L));

    public RagPrompt build(List<ChatMessage> historyMessages, String question, List<RetrievedChunk> sources) {
        Objects.requireNonNull(historyMessages, "historyMessages must not be null");
        Objects.requireNonNull(sources, "sources must not be null");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be null or blank");
        }

        String normalizedQuestion = question.strip();
        String userPrompt = buildUserPrompt(historyMessages, normalizedQuestion, sources);
        RagPrompt prompt = new RagPrompt(SYSTEM_PROMPT, userPrompt);
        log.debug("Chat prompt 构建完成, historyCount={}, sourceCount={}, systemLength={}, userLength={}",
                historyMessages.size(), sources.size(), prompt.system().length(), prompt.user().length());
        return prompt;
    }

    private String buildUserPrompt(List<ChatMessage> historyMessages, String question, List<RetrievedChunk> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("历史对话:\n");
        if (historyMessages.isEmpty()) {
            sb.append("（无历史对话）");
        } else {
            sb.append(historyMessages.stream()
                    .map(this::formatHistoryMessage)
                    .collect(Collectors.joining("\n\n")));
        }

        sb.append("\n\n候选笔记片段（按与当前问题的相关性从高到低排列）:");
        if (sources.isEmpty()) {
            sb.append("\n（未检索到相关笔记片段）");
            sb.append("\n\n当前问题:\n").append(question);
            appendNoSourcesOutputRequirements(sb);
            return sb.toString();
        }

        sb.append("\n可用 sourceId：")
                .append(sources.stream()
                        .map(RetrievedChunk::getChunkId)
                        .map(this::requireChunkId)
                        .map(String::valueOf)
                        .collect(Collectors.joining(", ")));

        for (RetrievedChunk chunk : sources) {
            sb.append("\n\nsourceId: ").append(requireChunkId(chunk.getChunkId())).append("\n");
            sb.append(RagTextFormatter.formatChunkContext(
                    chunk.getTitle(), chunk.getHeadingPath(), chunk.getContent()));
        }

        sb.append("\n\n当前问题:\n").append(question);
        appendOutputRequirements(sb);
        return sb.toString();
    }

    private Long requireChunkId(Long chunkId) {
        if (chunkId == null) {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR, "Retrieved chunk id must not be null");
        }
        return chunkId;
    }

    private void appendOutputRequirements(StringBuilder sb) {
        sb.append("\n\n输出要求:\n");
        sb.append("1. 直接回答当前问题。\n");
        sb.append("2. 每个事实句、列表项、对比点末尾都必须带 citation marker。\n");
        sb.append("3. 对比类问题优先使用短列表，不要用表格。\n");
        sb.append("4. 只能引用上方候选片段中的 sourceId。\n");
        sb.append("5. 如果无法添加合法 citation marker，只回答：根据当前笔记内容无法确定。");
    }

    private void appendNoSourcesOutputRequirements(StringBuilder sb) {
        sb.append("\n\n输出要求:\n");
        sb.append("只回答：根据当前笔记内容无法确定。");
    }

    private String formatHistoryMessage(ChatMessage message) {
        String roleLabel;
        if (message.getRole() == ChatMessageRole.USER) {
            roleLabel = "用户";
        } else if (message.getRole() == ChatMessageRole.ASSISTANT) {
            roleLabel = "助手";
        } else {
            throw new BusinessException(CodeStatus.INTERNAL_ERROR,
                    "Unsupported chat message role: " + message.getRole());
        }
        String sanitizedContent = stripCitationMarkers(message.getContent()).strip();
        return roleLabel + ":\n" + sanitizedContent;
    }

    private String stripCitationMarkers(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return CitationMarkers.markerPattern().matcher(text).replaceAll("");
    }
}
