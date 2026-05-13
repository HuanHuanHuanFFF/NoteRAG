package com.huanf.noterag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.huanf.noterag.model.NoteChunk;

@Mapper
public interface NoteChunkMapper {

    @Insert({
            "<script>",
            "INSERT INTO note_chunks (note_id, chunk_index, heading_path, content, char_count, token_count)",
            "VALUES",
            "<foreach collection='chunks' item='chunk' separator=','>",
            "(#{chunk.noteId}, #{chunk.chunkIndex}, #{chunk.headingPath}, #{chunk.content},",
            "#{chunk.charCount}, #{chunk.tokenCount})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("chunks") List<NoteChunk> chunks);

    @Select({
            "<script>",
            "INSERT INTO note_chunks (note_id, chunk_index, heading_path, content, char_count, token_count)",
            "VALUES",
            "<foreach collection='chunks' item='chunk' separator=','>",
            "(#{chunk.noteId}, #{chunk.chunkIndex}, #{chunk.headingPath}, #{chunk.content},",
            "#{chunk.charCount}, #{chunk.tokenCount})",
            "</foreach>",
            "RETURNING id,",
            "          note_id AS noteId,",
            "          chunk_index AS chunkIndex,",
            "          heading_path AS headingPath,",
            "          content,",
            "          char_count AS charCount,",
            "          token_count AS tokenCount,",
            "          created_at AS createdAt",
            "</script>"
    })
    List<NoteChunk> batchInsertReturning(@Param("chunks") List<NoteChunk> chunks);

    @Select("""
            SELECT id,
                   note_id AS noteId,
                   chunk_index AS chunkIndex,
                   heading_path AS headingPath,
                   content,
                   char_count AS charCount,
                   token_count AS tokenCount,
                   created_at AS createdAt
            FROM note_chunks
            WHERE note_id = #{documentId}
            ORDER BY chunk_index
            """)
    List<NoteChunk> findByDocumentId(@Param("documentId") Long documentId);
}
