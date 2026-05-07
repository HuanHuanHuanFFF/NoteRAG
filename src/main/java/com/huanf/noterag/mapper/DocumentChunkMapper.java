package com.huanf.noterag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.huanf.noterag.model.DocumentChunk;

@Mapper
public interface DocumentChunkMapper {

    @Insert({
            "<script>",
            "INSERT INTO document_chunks (document_id, chunk_index, heading_path, content, char_count, token_count)",
            "VALUES",
            "<foreach collection='chunks' item='chunk' separator=','>",
            "(#{chunk.documentId}, #{chunk.chunkIndex}, #{chunk.headingPath}, #{chunk.content},",
            "#{chunk.charCount}, #{chunk.tokenCount})",
            "</foreach>",
            "</script>"
    })
    int batchInsert(@Param("chunks") List<DocumentChunk> chunks);

    @Select("""
            SELECT id,
                   document_id AS documentId,
                   chunk_index AS chunkIndex,
                   heading_path AS headingPath,
                   content,
                   char_count AS charCount,
                   token_count AS tokenCount,
                   created_at AS createdAt
            FROM document_chunks
            WHERE document_id = #{documentId}
            ORDER BY chunk_index
            """)
    List<DocumentChunk> findByDocumentId(@Param("documentId") Long documentId);
}
