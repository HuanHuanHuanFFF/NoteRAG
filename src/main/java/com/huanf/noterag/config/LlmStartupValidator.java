package com.huanf.noterag.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * LLM 启动期校验。
 *
 * <p>`noterag.llm.enabled=false` 时允许没有 ChatModel，便于只调试检索链路。
 * `noterag.llm.enabled=true` 时启动阶段必须已经存在 Spring AI `ChatModel`，
 * 避免第一次 /api/query 调用才暴露模型配置错误。</p>
 */
@Component
public class LlmStartupValidator implements SmartInitializingSingleton {

    private final LlmProperties llmProperties;
    private final ObjectProvider<ChatModel> chatModelProvider;

    public LlmStartupValidator(LlmProperties llmProperties, ObjectProvider<ChatModel> chatModelProvider) {
        this.llmProperties = llmProperties;
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!llmProperties.isEnabled()) {
            return;
        }

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new IllegalStateException(
                    "noterag.llm.enabled=true requires a Spring AI ChatModel bean. "
                            + "Set spring.ai.model.chat=openai and provide the required chat model config.");
        }
    }
}
