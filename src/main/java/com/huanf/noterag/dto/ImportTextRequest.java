package com.huanf.noterag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Markdown 文本导入请求。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ImportTextRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 100_000)
    private String content;
}
