package com.huanf.noterag.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 检索调试请求。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalSearchRequest {

    @NotBlank
    @Size(max = 2_000)
    private String question;

    @Min(1)
    private Integer topN;

    @Size(max = 100)
    private List<Long> noteIds;
}
