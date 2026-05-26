package com.huanf.noterag.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.huanf.noterag.entity.Note;
import com.huanf.noterag.model.NoteListItem;

/**
 * Note 数据持久化。
 */
@Mapper
public interface NoteMapper {

    /**
     * 插入 note 并返回主键。
     */
    @Insert("""
            INSERT INTO notes (title, content, char_count, token_count)
            VALUES (#{title}, #{content}, #{charCount}, #{tokenCount})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Note note);

    /**
     * 按主键查询 ACTIVE note；已归档 note 对读取接口表现为不存在。
     */
    @Select("""
            SELECT id,
                   title,
                   content,
                   status,
                   char_count AS charCount,
                   token_count AS tokenCount,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM notes
            WHERE id = #{id}
              AND status = 'ACTIVE'
            """)
    Note findById(@Param("id") Long id);

    /**
     * 查询 ACTIVE 笔记列表所需的摘要信息，按创建时间倒序返回。
     */
    @Select("""
            SELECT n.id,
                   n.title,
                   n.char_count AS charCount,
                   n.token_count AS tokenCount,
                   CAST(COUNT(nc.id) AS INTEGER) AS chunkCount,
                   n.created_at AS createdAt
            FROM notes n
            LEFT JOIN note_chunks nc ON nc.note_id = n.id
            WHERE n.status = 'ACTIVE'
            GROUP BY n.id, n.title, n.char_count, n.token_count, n.created_at
            ORDER BY n.created_at DESC, n.id DESC
            """)
    List<NoteListItem> findSummaries();

    /**
     * 将 ACTIVE note 归档，返回受影响行数。
     */
    @Update("""
            UPDATE notes
            SET status = 'ARCHIVED'
            WHERE id = #{id}
              AND status = 'ACTIVE'
            """)
    int archiveById(@Param("id") Long id);
}
