package com.huanf.noterag.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

import com.huanf.noterag.model.ChunkEmbedding1024;

@Mapper
public interface ChunkEmbedding1024Mapper {

    @Insert("""
            INSERT INTO chunk_embeddings_1024 (note_chunk_id, embedding_model_id, embedding)
            VALUES (#{noteChunkId}, #{embeddingModelId},
                    #{embedding,typeHandler=com.huanf.noterag.mapper.typehandler.PgVectorFloatArrayTypeHandler}::vector)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChunkEmbedding1024 chunkEmbedding);
}
