package com.huanf.noterag.common.result;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一业务状态码，负责绑定业务码、默认消息和 HTTP 状态。
 */
@Getter
public enum CodeStatus {
    SUCCESS(0, "success", HttpStatus.OK),
    INVALID_REQUEST(40001, "请求参数错误", HttpStatus.BAD_REQUEST),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    DOCUMENT_NOT_FOUND(40401, "文档不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE(40600, "响应媒体类型不支持", HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE(41500, "请求媒体类型不支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    CHUNK_METADATA_INVALID(50001, "Chunk metadata 异常", HttpStatus.INTERNAL_SERVER_ERROR),
    EMBEDDING_DIMENSION_UNSUPPORTED(50002, "Embedding 维度暂不支持", HttpStatus.INTERNAL_SERVER_ERROR),
    EMBEDDING_MODEL_NOT_FOUND(50003, "Embedding 模型配置不存在", HttpStatus.INTERNAL_SERVER_ERROR),
    EMBEDDING_CONFIG_INVALID(50004, "Embedding configuration invalid", HttpStatus.INTERNAL_SERVER_ERROR),
    RERANK_CONFIG_INVALID(50005, "Rerank configuration invalid", HttpStatus.INTERNAL_SERVER_ERROR),
    EMBEDDING_FAILED(50201, "Embedding 服务调用失败", HttpStatus.BAD_GATEWAY),
    EMBEDDING_RESULT_INVALID(50202, "Embedding 结果异常", HttpStatus.BAD_GATEWAY),
    RERANK_FAILED(50203, "Rerank 服务调用失败", HttpStatus.BAD_GATEWAY),
    RERANK_RESULT_INVALID(50204, "Rerank 结果异常", HttpStatus.BAD_GATEWAY),
    INTERNAL_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    CodeStatus(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
