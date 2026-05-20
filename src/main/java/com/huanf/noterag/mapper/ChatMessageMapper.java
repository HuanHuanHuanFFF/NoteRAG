package com.huanf.noterag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.huanf.noterag.entity.ChatMessage;

/**
 * Chat 消息持久化。
 */
@Mapper
public interface ChatMessageMapper {

    /**
     * 插入 chat 消息并返回主键。
     */
    @Select("""
            INSERT INTO chat_messages (session_id, role, content, status, error_code, char_count)
            VALUES (#{sessionId}, #{role}, COALESCE(#{content}, ''), #{status}, #{errorCode},
                    COALESCE(#{charCount}, 0))
            RETURNING id
            """)
    Long insert(ChatMessage chatMessage);

    /**
     * 查询某个会话下可进入 prompt 的最近 N 条历史消息，
     * 排除当前 user 消息和未完成的 assistant 消息，并按时间正序返回。
     */
    @Select("""
            SELECT id,
                   session_id AS sessionId,
                   role,
                   content,
                   status,
                   error_code AS errorCode,
                   char_count AS charCount,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM (
                SELECT *
                FROM chat_messages
                WHERE session_id = #{sessionId}
                  AND id <> #{currentUserMessageId}
                  AND (
                      role = 'USER'
                      OR (role = 'ASSISTANT' AND status = 'COMPLETED')
                  )
                ORDER BY created_at DESC, id DESC
                LIMIT #{limit}
            ) recent_messages
            ORDER BY created_at, id
            """)
    List<ChatMessage> findPromptHistoryBySessionId(
            @Param("sessionId") Long sessionId,
            @Param("currentUserMessageId") Long currentUserMessageId,
            @Param("limit") int limit);

    /**
     * 查询某个会话下的全部消息，按时间正序返回。
     */
    @Select("""
            SELECT id,
                   session_id AS sessionId,
                   role,
                   content,
                   status,
                   error_code AS errorCode,
                   char_count AS charCount,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM chat_messages
            WHERE session_id = #{sessionId}
            ORDER BY created_at, id
            """)
    List<ChatMessage> findBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 更新 assistant 消息的内容和处理状态。
     */
    @Update("""
            UPDATE chat_messages
            SET content = #{content},
                status = #{status},
                error_code = #{errorCode},
                char_count = #{charCount}
            WHERE id = #{id}
              AND role = 'ASSISTANT'
              AND status = 'PENDING'
            """)
    int updateResult(ChatMessage chatMessage);
}
