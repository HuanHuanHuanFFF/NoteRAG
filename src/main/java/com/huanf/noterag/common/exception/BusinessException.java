package com.huanf.noterag.common.exception;

import com.huanf.noterag.common.result.CodeStatus;
import lombok.Getter;

/**
 * 基础业务异常，携带统一业务状态码。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final CodeStatus codeStatus;

    /**
     * 使用状态码默认消息创建业务异常。
     */
    public BusinessException(CodeStatus codeStatus) {
        super(codeStatus.getMessage());
        this.codeStatus = codeStatus;
    }

    /**
     * 使用自定义消息创建业务异常。
     */
    public BusinessException(CodeStatus codeStatus, String message) {
        super(message);
        this.codeStatus = codeStatus;
    }

    public BusinessException(CodeStatus codeStatus, String message, Throwable cause) {
        super(message, cause);
        this.codeStatus = codeStatus;
    }

}
