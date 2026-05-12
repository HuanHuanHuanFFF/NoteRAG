package com.huanf.noterag.config;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
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
    public EmbeddingClient embeddingClient(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                return new SpringAiEmbeddingClient(embeddingModelProvider.getObject()).embed(text);
            }

            @Override
            public List<float[]> embedAll(List<String> texts) {
                return new SpringAiEmbeddingClient(embeddingModelProvider.getObject()).embedAll(texts);
            }
        };
    }
}
