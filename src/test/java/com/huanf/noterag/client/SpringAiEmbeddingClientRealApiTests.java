package com.huanf.noterag.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.huanf.noterag.config.EmbeddingClientConfig;

/**
 * 手动触发的真实 Embedding API 测试。
 *
 * <p>该测试会实际调用外部模型服务，默认不参与普通 mvn test。需要显式设置
 * RUN_REAL_EMBEDDING_TEST=true，并提供 Spring AI embedding 相关环境变量。</p>
 */
@SpringBootTest(classes = SpringAiEmbeddingClientRealApiTests.RealEmbeddingTestApplication.class)
@EnabledIfEnvironmentVariable(named = "RUN_REAL_EMBEDDING_TEST", matches = "true")
class SpringAiEmbeddingClientRealApiTests {

    private static final int EXPECTED_DIMENSION = 1024;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Test
    void embedCallsRealProvider() {
        float[] embedding = embeddingClient.embed("衣服的质量杠杠的");
        System.out.println("dimension=" + embedding.length);
        System.out.println("first4=" + Arrays.toString(Arrays.copyOf(embedding, 4)) + "...");
        assertThat(embedding).isNotEmpty();
        assertThat(embedding).hasSize(EXPECTED_DIMENSION);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(EmbeddingClientConfig.class)
    static class RealEmbeddingTestApplication {
    }
}
