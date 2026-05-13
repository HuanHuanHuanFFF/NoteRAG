package com.huanf.noterag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Embedding 启动期校验。
 *
 * <p>`noterag.embedding.enabled=false` 时，应用允许不配置外部模型，便于只调试导入和切块。
 * `noterag.embedding.enabled=true` 时，启动阶段必须已经存在 Spring AI 的 `EmbeddingModel` bean；
 * 这样可以把 `spring.ai.model.embedding`、API key、base-url、model 等配置错误提前暴露，
 * 避免第一次导入触发 embedding 时才失败。</p>
 */
@Component
public class EmbeddingStartupValidator implements SmartInitializingSingleton {

    private final EmbeddingProperties embeddingProperties;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public EmbeddingStartupValidator(
            EmbeddingProperties embeddingProperties,
            ObjectProvider<EmbeddingModel> embeddingModelProvider
    ) {
        this.embeddingProperties = embeddingProperties;
        this.embeddingModelProvider = embeddingModelProvider;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!embeddingProperties.isEnabled()) {
            return;
        }

        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new IllegalStateException(
                    "noterag.embedding.enabled=true requires a Spring AI EmbeddingModel bean. "
                            + "Set spring.ai.model.embedding=openai and provide the required model provider config.");
        }
    }
}
