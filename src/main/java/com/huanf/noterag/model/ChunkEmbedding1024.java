package com.huanf.noterag.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChunkEmbedding1024 {

    private Long id;
    private Long chunkId;
    private Long embeddingModelId;
    private float[] embedding;
    private Instant createdAt;
}
