package com.huanf.noterag.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import com.huanf.noterag.model.ChunkEmbedding1024;

class ChunkEmbedding1024MapperTests {

    @Test
    void insertSqlMapsChunkModelAndPgvectorEmbeddingColumns() throws NoSuchMethodException {
        Method insertMethod = ChunkEmbedding1024Mapper.class.getMethod("insert", ChunkEmbedding1024.class);
        Insert insert = insertMethod.getAnnotation(Insert.class);

        assertThat(insert).isNotNull();
        String sql = String.join(" ", insert.value());
        assertThat(sql).contains("chunk_embeddings_1024");
        assertThat(sql).contains("note_chunk_id, embedding_model_id, embedding");
        assertThat(sql).contains("#{noteChunkId}");
        assertThat(sql).contains("#{embeddingModelId}");
        assertThat(sql).contains(
                "#{embedding,typeHandler=com.huanf.noterag.mapper.typehandler.PgVectorFloatArrayTypeHandler}::vector");
    }
}
