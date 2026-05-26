package com.huanf.noterag.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Query 调试请求。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {

    @NotBlank
    @Size(max = 2_000)
    private String question;

    @Size(max = 100)
    private List<Long> noteIds;
}
