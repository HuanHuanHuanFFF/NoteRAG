package com.huanf.noterag.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Markdown 文本导入响应。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportTextResponse {

    private Long documentId;
    private Integer chunkCount;
    private Integer charCount;
    private Integer tokenCount;
}
