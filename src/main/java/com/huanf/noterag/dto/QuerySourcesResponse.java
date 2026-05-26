package com.huanf.noterag.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Query sources 调试响应。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuerySourcesResponse {

    private List<SourceChunkResponse> sources;
}
