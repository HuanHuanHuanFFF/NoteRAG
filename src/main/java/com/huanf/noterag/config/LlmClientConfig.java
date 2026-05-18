package com.huanf.noterag.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huanf.noterag.client.LlmClient;
import com.huanf.noterag.client.NoopLlmClient;
import com.huanf.noterag.client.SpringAiLlmClient;

/**
 * LLM client 配置。
 *
 * <p>LLM 关闭时返回 no-op client，保证 /api/query 仍可作为 retrieval/rerank 调试入口。
 * LLM 开启时依赖 Spring AI 的通用 ChatModel，不在业务代码中绑定具体 provider。</p>
 */
@Configuration
public class LlmClientConfig {

    @Bean
    public LlmClient llmClient(LlmProperties llmProperties, ObjectProvider<ChatModel> chatModelProvider) {
        if (!llmProperties.isEnabled()) {
            return new NoopLlmClient();
        }
        return new SpringAiLlmClient(chatModelProvider.getObject());
    }
}
