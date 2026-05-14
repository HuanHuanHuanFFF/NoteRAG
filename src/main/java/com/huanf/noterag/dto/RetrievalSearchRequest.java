package com.huanf.noterag.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
