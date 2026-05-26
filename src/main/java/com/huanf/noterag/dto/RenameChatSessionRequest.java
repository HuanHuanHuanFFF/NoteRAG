package com.huanf.noterag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chat 会话重命名请求。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RenameChatSessionRequest {

    @NotBlank(message = "title must not be blank")
    @Size(max = 50, message = "title must not be greater than 50 characters")
    private String title;
}
