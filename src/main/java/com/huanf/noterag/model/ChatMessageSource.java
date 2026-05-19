package com.huanf.noterag.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chat 回答引用来源实体，对应 chat_message_sources 表。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageSource {

    private Long id;
    private Long messageId;
    private Long noteChunkId;

    /**
     * 来源在回答中的首次引用顺序。
     */
    private Integer sourceOrder;

    /**
     * 该来源在检索或重排阶段的分数快照。
     */
    private Double score;

    private Instant createdAt;
}
