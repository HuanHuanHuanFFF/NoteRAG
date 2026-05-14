package com.huanf.noterag.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class ChunkRetrievalMapperTests {

    @Test
    void searchTopNSqlJoinsChunkEmbeddingChunkAndNoteWithPgvectorOrdering() throws NoSuchMethodException {
        Method method = ChunkRetrievalMapper.class.getMethod("searchTopN", Long.class, float[].class, int.class);

        Select select = method.getAnnotation(Select.class);

        assertThat(select).isNotNull();
        String sql = String.join("\n", select.value());
        assertThat(sql).contains("chunk_embeddings_1024 ce");
        assertThat(sql).contains("JOIN note_chunks nc ON nc.id = ce.note_chunk_id");
        assertThat(sql).contains("JOIN notes n ON n.id = nc.note_id");
        assertThat(sql).contains("WHERE ce.embedding_model_id = #{embeddingModelId}");
        assertThat(sql).contains("<=>");
        assertThat(sql).contains("ORDER BY ce.embedding <=> query_vector.embedding");
        assertThat(sql).contains("LIMIT #{topN}");
        assertThat(sql).contains("1 - (ce.embedding <=> query_vector.embedding) AS score");
        assertThat(sql).contains("n.id AS noteId");
        assertThat(sql).contains("nc.id AS chunkId");
        assertThat(sql).contains("n.title AS title");
        assertThat(sql).contains("nc.heading_path AS headingPath");
        assertThat(sql).contains("nc.content AS content");
        assertThat(sql).contains(
                "#{queryEmbedding,typeHandler=com.huanf.noterag.mapper.typehandler.PgVectorFloatArrayTypeHandler}::vector");
    }
}
