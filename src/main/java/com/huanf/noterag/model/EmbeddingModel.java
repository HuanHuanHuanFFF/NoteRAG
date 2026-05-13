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
public class EmbeddingModel {

    private Long id;
    private String provider;
    private String modelName;
    private String displayName;
    private Integer dimension;
    private String distanceMetric;
    private String baseUrl;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
