package com.huanf.noterag.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.huanf.noterag.dto.QueryResponse;
import com.huanf.noterag.dto.SourceChunkResponse;
import com.huanf.noterag.model.RetrievedChunk;

@Service
public class QueryService {

    private final RetrievalService retrievalService;
    private final RerankService rerankService;

    public QueryService(RetrievalService retrievalService, RerankService rerankService) {
        this.retrievalService = retrievalService;
        this.rerankService = rerankService;
    }

    public QueryResponse query(String question) {
        List<RetrievedChunk> retrievedChunks = retrievalService.retrieveTopN(question);
        List<RetrievedChunk> rerankedChunks = rerankService.rerank(question, retrievedChunks);
        List<SourceChunkResponse> sources = rerankedChunks.stream()
                .map(SourceChunkResponse::from)
                .toList();
        return new QueryResponse("", sources);
    }
}
