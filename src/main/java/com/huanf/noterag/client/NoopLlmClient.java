package com.huanf.noterag.client;

import com.huanf.noterag.rag.RagPrompt;

/**
 * LLM 关闭时使用的占位 client。
 *
 * <p>用于保持 /api/query 的 retrieval/rerank 调试能力，避免本地未配置 chat model 时阻断查询链路。</p>
 */
public class NoopLlmClient implements LlmClient {

    @Override
    public String chat(RagPrompt prompt) {
        if (prompt == null) {
            throw new IllegalArgumentException("prompt must not be null");
        }
        return "";
    }
}
