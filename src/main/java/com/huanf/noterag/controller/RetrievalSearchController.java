package com.huanf.noterag.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huanf.noterag.dto.RetrievalSearchRequest;
import com.huanf.noterag.dto.RetrievalSearchResponse;
import com.huanf.noterag.dto.SourceChunkResponse;
import com.huanf.noterag.model.RetrievedChunk;
import com.huanf.noterag.service.RetrievalService;

import jakarta.validation.Valid;

/**
 * Retrieval 调试接口。
 *
 * <p>当前只暴露 query embedding + pgvector TopN 召回结果，不负责 rerank、prompt 拼接或 LLM 回答。</p>
 */
@RestController
@RequestMapping("/api")
public class RetrievalSearchController {

    private final RetrievalService retrievalService;

    public RetrievalSearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping(value = "/retrieval/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RetrievalSearchResponse search(@Valid @RequestBody RetrievalSearchRequest request) {
        List<RetrievedChunk> chunks;
        if (request.getTopN() == null) {
            chunks = retrievalService.retrieveTopN(request.getQuestion());
        } else {
            chunks = retrievalService.retrieveTopN(request.getQuestion(), request.getTopN());
        }

        List<SourceChunkResponse> sources = chunks.stream()
                .map(SourceChunkResponse::from)
                .toList();
        return new RetrievalSearchResponse(sources);
    }
}
