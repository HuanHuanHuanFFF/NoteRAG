package com.huanf.noterag.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huanf.noterag.dto.QueryRequest;
import com.huanf.noterag.dto.QuerySourcesResponse;
import com.huanf.noterag.dto.SourceChunkResponse;
import com.huanf.noterag.service.QueryService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public QuerySourcesResponse query(@Valid @RequestBody QueryRequest request) {
        log.info("Query sources 请求, questionLength={}, noteScopeCount={}",
                request.getQuestion().length(), noteScopeCount(request.getNoteIds()));
        return new QuerySourcesResponse(
                queryService.querySources(request.getQuestion(), request.getNoteIds()).stream()
                        .map(SourceChunkResponse::from)
                        .toList());
    }

    /**
     * 统计请求中的 note scope 数量，仅用于日志，不在 controller 做 scope 清理。
     */
    private int noteScopeCount(List<Long> noteIds) {
        return noteIds == null ? 0 : noteIds.size();
    }
}
