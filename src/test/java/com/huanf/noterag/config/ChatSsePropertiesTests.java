package com.huanf.noterag.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ChatSsePropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsSseExecutorProperties() {
        contextRunner
                .withPropertyValues(
                        "noterag.chat.sse-executor.core-pool-size=3",
                        "noterag.chat.sse-executor.max-pool-size=6",
                        "noterag.chat.sse-executor.queue-capacity=30")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ChatSseProperties properties = context.getBean(ChatSseProperties.class);
                    assertThat(properties.getSseExecutor().getCorePoolSize()).isEqualTo(3);
                    assertThat(properties.getSseExecutor().getMaxPoolSize()).isEqualTo(6);
                    assertThat(properties.getSseExecutor().getQueueCapacity()).isEqualTo(30);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ChatSseProperties.class)
    static class TestConfiguration {
    }
}
