package com.huanf.noterag.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class EmbeddingStartupValidatorTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(EmbeddingProperties.class, EmbeddingStartupValidator.class);

    @Test
    void contextStartsWithoutEmbeddingModelWhenEmbeddingIsDisabled() {
        contextRunner
                .withPropertyValues("noterag.embedding.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void contextFailsWhenEmbeddingIsEnabledButEmbeddingModelIsMissing() {
        contextRunner
                .withPropertyValues(
                        "noterag.embedding.enabled=true",
                        "noterag.embedding.provider=openai",
                        "noterag.embedding.model-name=text-embedding-v4",
                        "noterag.embedding.dimension=1024",
                        "noterag.embedding.distance-metric=cosine")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("requires a Spring AI EmbeddingModel bean");
                });
    }

    @Test
    void contextStartsWhenEmbeddingIsEnabledAndEmbeddingModelExists() {
        contextRunner
                .withUserConfiguration(TestEmbeddingModelConfiguration.class)
                .withPropertyValues(
                        "noterag.embedding.enabled=true",
                        "noterag.embedding.provider=openai",
                        "noterag.embedding.model-name=text-embedding-v4",
                        "noterag.embedding.dimension=1024",
                        "noterag.embedding.distance-metric=cosine")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration
    static class TestEmbeddingModelConfiguration {

        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }
    }
}
