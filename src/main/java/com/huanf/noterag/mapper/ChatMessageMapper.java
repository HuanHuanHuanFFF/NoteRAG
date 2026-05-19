package com.huanf.noterag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.huanf.noterag.model.ChatMessage;

/**
 * Chat 消息持久化。
 */
@Mapper
public interface ChatMessageMapper {

    /**
     * 插入 chat 消息并回填主键。
     */
    @Insert("""
            INSERT INTO chat_messages (session_id, role, content, status, error_code, char_count)
            VALUES (#{sessionId}, #{role}, COALESCE(#{content}, ''), #{status}, #{errorCode},
                    COALESCE(#{charCount}, 0))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(ChatMessage chatMessage);

    /**
     * 查询某个会话下最近的 N 条消息，按时间倒序返回。
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
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<ChatMessage> findRecentBySessionId(@Param("sessionId") Long sessionId, @Param("limit") int limit);

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
