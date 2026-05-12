package com.huanf.noterag.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.huanf.noterag.model.EmbeddingModel;

@Mapper
public interface EmbeddingModelMapper {

    @Select("""
            SELECT id,
                   provider,
                   model_name AS modelName,
                   dimension,
                   distance_metric AS distanceMetric,
                   base_url AS baseUrl,
                   enabled,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM embedding_models
            WHERE provider = #{provider}
              AND model_name = #{modelName}
              AND dimension = #{dimension}
              AND distance_metric = #{distanceMetric}
              AND enabled = true
            ORDER BY id
            LIMIT 1
            """)
    EmbeddingModel findEnabledBySpec(@Param("provider") String provider,
            @Param("modelName") String modelName,
            @Param("dimension") Integer dimension,
            @Param("distanceMetric") String distanceMetric);
}
