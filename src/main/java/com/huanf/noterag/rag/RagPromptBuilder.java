package com.huanf.noterag.rag;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.model.RetrievedChunk;

@Slf4j
@Component
public class RagPromptBuilder {

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是 NoteRAG 的笔记问答助手。只根据候选笔记片段回答；可以概括、合并、重排片段信息，但不能改变片段含义或补充外部事实。
            
            证据：
            1. 片段按相关性从高到低排列，优先使用靠前且能直接回答问题的正文片段；参考资料、链接、目录等低信息片段只有确实有用或用户问出处时才用。
            2. 如果没有可用片段、片段都无关，或用户提示出现“未检索到相关笔记片段”，只回答：根据当前笔记内容无法确定。
            3. 片段可能被截断；缺上下文时谨慎表述，可写“根据片段有限信息”。片段互相矛盾时，同时说明不同说法并分别引用。
            
            回答：
            1. 先直接回答，语言跟随用户；不要机械复述片段，尽量整理成连贯回答。
            2. 可使用 Markdown 段落、列表、表格、小标题；代码、命令、SQL、日志可用代码块。
            3. 不输出“参考来源”“来源”“基于片段”“根据第 x 个片段”等文字来源说明，来源只用 citation marker 表达，前端会负责渲染。
            
            引用：
            1. 凡是使用了某个片段的信息，必须在对应句子末尾添加 citation marker。
            2. marker 格式：{{citationMarkerFormat}}。紧跟句号、问号、感叹号、冒号或分号，不加空格；多个 marker 连续书写，例如：MySQL 的 MVCC 依赖隐藏字段、Read View 和 undo log。{{citationExample}}
            3. 只能引用候选 sourceId，且只引用支撑该句的最少必要片段；不要在正文中直接写 sourceId。
            4. 若使用 Markdown 代码块，citation marker 放在代码块前后的解释句末尾，严禁写进代码块内部。
            """;
    private static final String SYSTEM_PROMPT = SYSTEM_PROMPT_TEMPLATE
            .replace("{{citationMarkerFormat}}", CitationMarkers.formatPlaceholder())
            .replace("{{citationExample}}", CitationMarkers.format(140L) + CitationMarkers.format(141L));

    public RagPrompt build(String question, List<RetrievedChunk> sources) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be null or blank");
        }
        if (sources == null) {
            throw new IllegalArgumentException("sources must not be null");
        }
        if (sources.isEmpty()) {
            log.warn("Prompt 构建时 sources 为空, questionLength={}", question.length());
        }
        String normalizedQuestion = question.strip();
        RagPrompt prompt = new RagPrompt(SYSTEM_PROMPT, buildUserPrompt(normalizedQuestion, sources));
        if (log.isDebugEnabled()) {
            log.debug("Prompt 构建完成, sourceCount={}, sourceIds={}, systemLength={}, userLength={}",
                    sources.size(),
                    sources.stream().map(c -> c.getChunkId() + "(" + truncate(c.getTitle(), 15) + "/" + truncate(c.getHeadingPath(), 20) + ")").toList(),
                    prompt.system().length(),
                    prompt.user().length());
        }
        return prompt;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null || text.isEmpty()) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String buildUserPrompt(String question, List<RetrievedChunk> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题:\n").append(question).append("\n\n笔记片段（按与问题的相关性从高到低排列）:");
        if (sources.isEmpty()) {
            sb.append("\n（未检索到相关笔记片段）");
            return sb.toString();
        }
        for (int i = 0; i < sources.size(); i++) {
            RetrievedChunk chunk = sources.get(i);
            if (chunk.getChunkId() == null) {
                throw new BusinessException(CodeStatus.INTERNAL_ERROR, "Retrieved chunk id must not be null");
            }
            sb.append("\n\nsourceId: ").append(chunk.getChunkId()).append("\n");
            sb.append(RagTextFormatter.formatChunkContext(
                    chunk.getTitle(), chunk.getHeadingPath(), chunk.getContent()));
        }
        return sb.toString();
    }
}
