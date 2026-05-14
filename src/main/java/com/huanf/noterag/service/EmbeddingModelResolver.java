package com.huanf.noterag.service;

import org.springframework.stereotype.Component;

import com.huanf.noterag.common.exception.BusinessException;
import com.huanf.noterag.common.result.CodeStatus;
import com.huanf.noterag.config.EmbeddingProperties;
import com.huanf.noterag.mapper.EmbeddingModelMapper;
import com.huanf.noterag.model.EmbeddingModel;

/**
 * 解析当前启用的 embedding model 配置。
 *
 * <p>当前向量表固定为 1024 维，因此这里集中校验运行配置、数据库模型记录和维度约束，
 * 避免导入向量化和查询检索各自维护一份模型解析逻辑。</p>
 */
@Component
public class EmbeddingModelResolver {

    private static final int SUPPORTED_DIMENSION_1024 = 1024;

    private final EmbeddingProperties embeddingProperties;
    private final EmbeddingModelMapper embeddingModelMapper;

    public EmbeddingModelResolver(
            EmbeddingProperties embeddingProperties,
            EmbeddingModelMapper embeddingModelMapper
    ) {
        this.embeddingProperties = embeddingProperties;
        this.embeddingModelMapper = embeddingModelMapper;
    }

    public EmbeddingModel resolveRequired1024Model() {
        if (!embeddingProperties.isEnabled()) {
            throw new BusinessException(CodeStatus.EMBEDDING_CONFIG_INVALID,
                    "Embedding is disabled. Set noterag.embedding.enabled=true before using embedding features.");
        }

        validateEmbeddingConfig();

        if (!Integer.valueOf(SUPPORTED_DIMENSION_1024).equals(embeddingProperties.getDimension())) {
            throw new BusinessException(CodeStatus.EMBEDDING_DIMENSION_UNSUPPORTED,
                    "Only 1024-dimension embeddings are supported currently, configured dimension=%d"
                            .formatted(embeddingProperties.getDimension()));
        }

        EmbeddingModel embeddingModel = embeddingModelMapper.findEnabledBySpec(
                embeddingProperties.getProvider(),
                embeddingProperties.getModelName(),
                embeddingProperties.getDimension(),
                embeddingProperties.getDistanceMetric());
        if (embeddingModel == null) {
            throw new BusinessException(CodeStatus.EMBEDDING_MODEL_NOT_FOUND,
                    "Enabled embedding model config not found: provider=%s, modelName=%s, dimension=%d, distanceMetric=%s"
                            .formatted(
                                    embeddingProperties.getProvider(),
                                    embeddingProperties.getModelName(),
                                    embeddingProperties.getDimension(),
                                    embeddingProperties.getDistanceMetric()));
        }
        validateEmbeddingModel(embeddingModel);
        return embeddingModel;
    }

    /**
     * 校验 embedding 功能配置。
     *
     * <p>应用可以在 embedding 关闭或未完整配置时启动；但真正执行 embedding 相关能力时，
     * provider、modelName、distanceMetric 和 dimension 必须完整有效。</p>
     */
    private void validateEmbeddingConfig() {
        if (isBlank(embeddingProperties.getProvider())
                || isBlank(embeddingProperties.getModelName())
                || isBlank(embeddingProperties.getDistanceMetric())
                || embeddingProperties.getDimension() == null
                || embeddingProperties.getDimension() <= 0) {
            throw new BusinessException(CodeStatus.EMBEDDING_CONFIG_INVALID,
                    "Embedding config is invalid: provider, modelName, distanceMetric must be set and dimension must be greater than 0");
        }
    }

    /**
     * 校验数据库中的 embedding model 配置与当前运行配置一致。
     */
    private void validateEmbeddingModel(EmbeddingModel embeddingModel) {
        if (embeddingModel.getId() == null) {
            throw new BusinessException(CodeStatus.EMBEDDING_MODEL_NOT_FOUND,
                    "Embedding model config id must not be null");
        }
        if (embeddingModel.getDimension() == null
                || !embeddingModel.getDimension().equals(embeddingProperties.getDimension())
                || !Integer.valueOf(SUPPORTED_DIMENSION_1024).equals(embeddingModel.getDimension())) {
            throw new BusinessException(CodeStatus.EMBEDDING_RESULT_INVALID,
                    "Embedding model dimension mismatch: expected=%d, actual=%s"
                            .formatted(SUPPORTED_DIMENSION_1024, embeddingModel.getDimension()));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
