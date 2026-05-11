package com.huanf.noterag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huanf.noterag.client.EmbeddingClient;
import com.huanf.noterag.client.SpringAiEmbeddingClient;

/**
 * Embedding client 配置。
 *
 * <p>只依赖 Spring AI 的通用 EmbeddingModel，不绑定具体 provider。</p>
 */
@Configuration
public class EmbeddingClientConfig {

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    public EmbeddingClient embeddingClient(EmbeddingModel embeddingModel) {
        return new SpringAiEmbeddingClient(embeddingModel);
    }
}
