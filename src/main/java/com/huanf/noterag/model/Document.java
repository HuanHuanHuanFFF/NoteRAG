package com.huanf.noterag.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    private Long id;
    private String title;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
}
