package com.huanf.noterag.entity;

/**
 * 通用记录状态，用于需要软删除/归档的主记录。
 */
public enum RecordStatus {

    /**
     * 活跃记录，可被默认列表、详情和业务流程使用。
     */
    ACTIVE,

    /**
     * 已归档记录，对外表现为不可见或不存在。
     */
    ARCHIVED
}
