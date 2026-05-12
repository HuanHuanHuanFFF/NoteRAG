package com.huanf.noterag.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.huanf.noterag.NoteRagApplication;
import com.huanf.noterag.client.EmbeddingClient;
import com.huanf.noterag.mapper.ChunkEmbedding1024Mapper;
import com.huanf.noterag.model.ChunkEmbedding1024;

/**
 * 手动触发的真实链路测试：调用外部 Embedding API，并把返回向量写入 PostgreSQL pgvector。
 *
 * <p>默认不参与普通测试。运行前需要启动 Docker PostgreSQL，并显式设置
 * RUN_REAL_EMBEDDING_DB_TEST=true 以及 Spring AI embedding 相关环境变量。</p>
 */
@SpringBootTest(classes = NoteRagApplication.class)
@EnabledIfEnvironmentVariable(named = "RUN_REAL_EMBEDDING_DB_TEST", matches = "true")
class RealEmbeddingInsertIntegrationTests {

    private static final int EXPECTED_DIMENSION = 1024;
    private static final String TEST_CONTENT = "HashMap 在 Java 中用于存储键值对，常见面试点包括哈希冲突、扩容和线程安全。";

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private ChunkEmbedding1024Mapper chunkEmbedding1024Mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void realRuntimeProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.autoconfigure.exclude", () -> "");
        registry.add("spring.datasource.url", () -> envOrDefault(
                "REAL_POSTGRES_JDBC_URL", "jdbc:postgresql://localhost:5432/noterag"));
        registry.add("spring.datasource.username", () -> envOrDefault("REAL_POSTGRES_USERNAME", "noterag"));
        registry.add("spring.datasource.password", () -> envOrDefault("REAL_POSTGRES_PASSWORD", "change-me"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.sql.init.mode", () -> "never");

        registry.add("spring.ai.model.chat", () -> "none");
        registry.add("spring.ai.model.embedding", () -> "openai");
        registry.add("spring.ai.model.image", () -> "none");
        registry.add("spring.ai.model.audio.speech", () -> "none");
        registry.add("spring.ai.model.audio.transcription", () -> "none");
        registry.add("spring.ai.model.moderation", () -> "none");
        registry.add("spring.ai.openai.api-key", () -> envOrDefault("SPRING_AI_OPENAI_API_KEY", ""));
        registry.add("spring.ai.openai.base-url", () -> envOrDefault(
                "SPRING_AI_OPENAI_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode"));
        registry.add("spring.ai.openai.embedding.options.model", () -> envOrDefault(
                "SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL", "text-embedding-v4"));
    }

    @Test
    void callsRealEmbeddingApiAndInsertsPgvectorRecord() {
        Long noteId = insertNote();
        Long noteChunkId = insertNoteChunk(noteId);
        Long embeddingModelId = upsertEmbeddingModel();

        float[] embedding = embeddingClient.embed(TEST_CONTENT);
        assertThat(embedding).hasSize(EXPECTED_DIMENSION);

        ChunkEmbedding1024 record = new ChunkEmbedding1024();
        record.setNoteChunkId(noteChunkId);
        record.setEmbeddingModelId(embeddingModelId);
        record.setEmbedding(embedding);

        int rows = chunkEmbedding1024Mapper.insert(record);

        assertThat(rows).isEqualTo(1);
        assertThat(record.getId()).isNotNull();

        Integer dimensions = jdbcTemplate.queryForObject(
                "SELECT vector_dims(embedding) FROM chunk_embeddings_1024 WHERE id = ?",
                Integer.class,
                record.getId());

        assertThat(dimensions).isEqualTo(EXPECTED_DIMENSION);
        System.out.println("noteId=" + noteId + ", noteChunkId=" + noteChunkId
                + ", embeddingId=" + record.getId() + ", dimension=" + dimensions);
        System.out.println("first4=" + Arrays.toString(Arrays.copyOf(embedding, 4)) + "...");
    }

    private Long insertNote() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notes (title, content, char_count, token_count)
                VALUES (?, ?, char_length(?), ?)
                RETURNING id
                """, Long.class, "real-embedding-insert-test", TEST_CONTENT, TEST_CONTENT, 64);
    }

    private Long insertNoteChunk(Long noteId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO note_chunks (note_id, chunk_index, heading_path, content, char_count, token_count)
                VALUES (?, 0, ?, ?, char_length(?), ?)
                RETURNING id
                """, Long.class, noteId, "Java > HashMap", TEST_CONTENT, TEST_CONTENT, 64);
    }

    private Long upsertEmbeddingModel() {
        String modelName = envOrDefault("SPRING_AI_OPENAI_EMBEDDING_OPTIONS_MODEL", "text-embedding-v4");
        String baseUrl = envOrDefault("SPRING_AI_OPENAI_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode");

        return jdbcTemplate.queryForObject("""
                INSERT INTO embedding_models
                    (provider, model_name, dimension, distance_metric, base_url, enabled)
                VALUES
                    (?, ?, ?, 'cosine', ?, true)
                ON CONFLICT (provider, model_name, dimension)
                DO UPDATE SET
                    distance_metric = EXCLUDED.distance_metric,
                    base_url = EXCLUDED.base_url,
                    enabled = true,
                    updated_at = now()
                RETURNING id
                """, Long.class, "openai", modelName, EXPECTED_DIMENSION, baseUrl);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
