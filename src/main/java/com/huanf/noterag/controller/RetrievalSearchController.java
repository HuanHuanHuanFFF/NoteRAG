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
import lombok.extern.slf4j.Slf4j;

/**
 * Retrieval 调试接口。
 *
 * <p>当前只暴露 query embedding + pgvector TopN 召回结果，不负责 rerank、prompt 拼接或 LLM 回答。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class RetrievalSearchController {

    private final RetrievalService retrievalService;

    public RetrievalSearchController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping(value = "/retrieval/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RetrievalSearchResponse search(@Valid @RequestBody RetrievalSearchRequest request) {
        log.info("Retrieval search 请求, questionLength={}, topN={}, noteScopeCount={}",
                request.getQuestion().length(), request.getTopN(), noteScopeCount(request.getNoteIds()));
        List<RetrievedChunk> chunks;
        if (request.getTopN() == null) {
            chunks = retrievalService.retrieveTopN(request.getQuestion(), request.getNoteIds());
        } else {
            chunks = retrievalService.retrieveTopN(request.getQuestion(), request.getTopN(), request.getNoteIds());
        }

        List<SourceChunkResponse> sources = chunks.stream()
                .map(SourceChunkResponse::from)
                .toList();
        log.info("Retrieval search 响应, returnedCount={}", sources.size());
        return new RetrievalSearchResponse(sources);
    }

    /**
     * 统计请求中的 note scope 数量，仅用于日志，不在 controller 做 scope 清理。
     */
    private int noteScopeCount(List<Long> noteIds) {
        return noteIds == null ? 0 : noteIds.size();
    }
}
