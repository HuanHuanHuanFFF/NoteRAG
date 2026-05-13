package com.huanf.noterag.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class NoteChunkMapperTests {

    @Test
    void batchInsertReturningUsesPostgresReturningSqlWithMappedColumns() throws NoSuchMethodException {
        Method method = NoteChunkMapper.class.getMethod("batchInsertReturning", List.class);

        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();

        String sql = String.join("\n", select.value());
        assertThat(sql).contains("INSERT INTO note_chunks");
        assertThat(sql).contains("<foreach collection='chunks' item='chunk' separator=','>");
        assertThat(sql).contains("RETURNING id");
        assertThat(sql).contains("note_id AS noteId");
        assertThat(sql).contains("chunk_index AS chunkIndex");
        assertThat(sql).contains("heading_path AS headingPath");
        assertThat(sql).contains("content");
        assertThat(sql).contains("char_count AS charCount");
        assertThat(sql).contains("token_count AS tokenCount");
        assertThat(sql).contains("created_at AS createdAt");
    }
}
