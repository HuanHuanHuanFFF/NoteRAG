package com.huanf.noterag.common.result;

import org.springframework.http.HttpStatus;

/**
 * 统一业务状态码，负责绑定业务码、默认消息和 HTTP 状态。
 */
public enum CodeStatus {
    SUCCESS(0, "success", HttpStatus.OK),
    INVALID_REQUEST(40001, "请求参数错误", HttpStatus.BAD_REQUEST),
    DOCUMENT_NOT_FOUND(40401, "文档不存在", HttpStatus.NOT_FOUND),
    CHUNK_METADATA_INVALID(50001, "Chunk metadata 异常", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String msg;
    private final HttpStatus httpStatus;

    CodeStatus(int code, String msg, HttpStatus httpStatus) {
        this.code = code;
        this.msg = msg;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
