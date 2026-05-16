package com.huanf.noterag.prompt;

import java.util.List;

import org.springframework.stereotype.Component;

import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.util.ChunkContextFormatter;

@Component
public class RagPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是 NoteRAG 的笔记问答助手，只能基于下方提供的笔记片段回答用户问题。

            严格规则:
            1. 仅依据"笔记片段"中的内容作答，不得使用任何外部知识或网络信息。
            2. 不得声称自己进行了联网搜索或查询了外部资料。
            3. 不得编造笔记片段中不存在的事实。
            4. 若提供的笔记片段不足以回答问题，必须直接回答：根据当前笔记内容无法确定。
            5. 使用与用户问题相同的语言作答。
            6. 引用笔记片段时使用 [1]、[2] 等编号。

            回答格式:
            回答:
            <在此给出回答>

            参考来源:
            [1] 文档标题 / 章节路径
            [2] ...""";

    public RagPrompt build(String question, List<RetrievedChunk> sources) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be null or blank");
        }
        if (sources == null) {
            throw new IllegalArgumentException("sources must not be null");
        }
        String normalizedQuestion = question.strip();
        return new RagPrompt(SYSTEM_PROMPT, buildUserPrompt(normalizedQuestion, sources));
    }

    private String buildUserPrompt(String question, List<RetrievedChunk> sources) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户问题:\n").append(question).append("\n\n笔记片段:");
        if (sources.isEmpty()) {
            sb.append("\n（未检索到相关笔记片段）");
            return sb.toString();
        }
        for (int i = 0; i < sources.size(); i++) {
            RetrievedChunk chunk = sources.get(i);
            sb.append("\n\n[").append(i + 1).append("]\n");
            sb.append(ChunkContextFormatter.formatChunkForEmbedding(
                    chunk.getTitle(), chunk.getHeadingPath(), chunk.getContent()));
        }
        return sb.toString();
    }
}
