package com.huanf.noterag.model;

/**
 * Chat 会话状态。
 */
public enum ChatSessionStatus {

    /**
     * 活跃会话，可继续发送消息。
     */
    ACTIVE,

    /**
     * 已归档会话，不再作为默认活跃会话使用。
     */
    ARCHIVED
}
