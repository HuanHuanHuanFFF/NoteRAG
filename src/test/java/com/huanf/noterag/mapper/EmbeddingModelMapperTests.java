package com.huanf.noterag.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.huanf.noterag.model.EmbeddingModel;

@MybatisTest
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:embedding-model-mapper;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql"
})
class EmbeddingModelMapperTests {

    @Autowired
    private EmbeddingModelMapper embeddingModelMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findEnabledBySpecMapsSqlColumnsToModelFields() {
        jdbcTemplate.update("""
                INSERT INTO embedding_models
                    (provider, model_name, dimension, distance_metric, base_url, enabled)
                VALUES
                    (?, ?, ?, ?, ?, ?)
                """, "openai", "text-embedding-3-large", 1024, "cosine", "https://api.example.test", true);

        EmbeddingModel model = embeddingModelMapper.findEnabledBySpec(
                "openai", "text-embedding-3-large", 1024, "cosine");

        assertThat(model).isNotNull();
        assertThat(model.getId()).isNotNull();
        assertThat(model.getProvider()).isEqualTo("openai");
        assertThat(model.getModelName()).isEqualTo("text-embedding-3-large");
        assertThat(model.getDimension()).isEqualTo(1024);
        assertThat(model.getDistanceMetric()).isEqualTo("cosine");
        assertThat(model.getBaseUrl()).isEqualTo("https://api.example.test");
        assertThat(model.getEnabled()).isTrue();
        assertThat(model.getCreatedAt()).isNotNull();
        assertThat(model.getUpdatedAt()).isNotNull();
    }

    @Test
    void findEnabledBySpecIgnoresDisabledModel() {
        jdbcTemplate.update("""
                INSERT INTO embedding_models
                    (provider, model_name, dimension, distance_metric, enabled)
                VALUES
                    (?, ?, ?, ?, ?)
                """, "openai", "disabled-model", 1024, "cosine", false);

        EmbeddingModel model = embeddingModelMapper.findEnabledBySpec(
                "openai", "disabled-model", 1024, "cosine");

        assertThat(model).isNull();
    }
}
