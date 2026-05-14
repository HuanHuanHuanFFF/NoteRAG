package com.huanf.noterag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.huanf.noterag.model.RetrievedChunk;

@Mapper
public interface ChunkRetrievalMapper {

    @Select("""
            WITH query_vector AS (
                SELECT #{queryEmbedding,typeHandler=com.huanf.noterag.mapper.typehandler.PgVectorFloatArrayTypeHandler}::vector AS embedding
            )
            SELECT n.id AS noteId,
                   nc.id AS chunkId,
                   n.title AS title,
                   nc.heading_path AS headingPath,
                   nc.content AS content,
                   1 - (ce.embedding <=> query_vector.embedding) AS score
            FROM chunk_embeddings_1024 ce
            JOIN note_chunks nc ON nc.id = ce.note_chunk_id
            JOIN notes n ON n.id = nc.note_id
            CROSS JOIN query_vector
            WHERE ce.embedding_model_id = #{embeddingModelId}
            ORDER BY ce.embedding <=> query_vector.embedding
            LIMIT #{topN}
            """)
    List<RetrievedChunk> searchTopN(@Param("embeddingModelId") Long embeddingModelId,
            @Param("queryEmbedding") float[] queryEmbedding,
            @Param("topN") int topN);
}
