package com.huanf.noterag.controller;

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

@RestController
@RequestMapping("/api")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public QuerySourcesResponse query(@Valid @RequestBody QueryRequest request) {
        return new QuerySourcesResponse(
                queryService.querySources(request.getQuestion()).stream()
                        .map(SourceChunkResponse::from)
                        .toList());
    }
}
