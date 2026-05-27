package com.huanf.noterag.client;


import java.util.function.Consumer;

import com.huanf.noterag.rag.RagPrompt;

/**
 * LLM 调用抽象，屏蔽具体模型供应商和 Spring AI 适配细节。
 */
public interface LlmClient {

    String chat(RagPrompt prompt);

    /**
     * 流式生成回答。
     *
     * <p>onDelta 是面向调用方的通知回调，调用失败不应该中断模型流消费和最终 answer 拼接。</p>
     */
    String streamChat(RagPrompt prompt, Consumer<String> onDelta);
}
