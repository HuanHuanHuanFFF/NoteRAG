package com.huanf.noterag.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huanf.noterag.client.LlmClient;
import com.huanf.noterag.client.NoopLlmClient;
import com.huanf.noterag.client.SpringAiLlmClient;

class LlmClientConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(LlmProperties.class, LlmClientConfig.class);

    @Test
    void createsNoopLlmClientWhenLlmIsDisabled() {
        contextRunner
                .withPropertyValues("noterag.llm.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(NoopLlmClient.class);
                });
    }

    @Test
    void createsSpringAiLlmClientWhenLlmIsEnabledAndChatModelExists() {
        contextRunner
                .withUserConfiguration(TestChatModelConfiguration.class)
                .withPropertyValues(
                        "noterag.llm.enabled=true",
                        "noterag.llm.provider=openai",
                        "noterag.llm.model-name=deepseek-v4-flash",
                        "noterag.llm.api-key=test-key",
                        "noterag.llm.base-url=https://example.com",
                        "noterag.llm.temperature=0.2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(SpringAiLlmClient.class);
                });
    }

    @Test
    void contextFailsWhenLlmIsEnabledButChatModelIsMissing() {
        contextRunner
                .withPropertyValues(
                        "noterag.llm.enabled=true",
                        "noterag.llm.provider=openai",
                        "noterag.llm.model-name=deepseek-v4-flash",
                        "noterag.llm.api-key=test-key",
                        "noterag.llm.base-url=https://example.com",
                        "noterag.llm.temperature=0.2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    static class TestChatModelConfiguration {

        @Bean
        ChatModel chatModel() {
            return mock(ChatModel.class);
        }
    }
}
