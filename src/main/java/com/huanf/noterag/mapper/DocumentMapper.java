package com.huanf.noterag.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.huanf.noterag.model.Document;

@Mapper
public interface DocumentMapper {

    @Insert("""
            INSERT INTO documents (title, content, char_count, token_count)
            VALUES (#{title}, #{content}, #{charCount}, #{tokenCount})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Document document);

    @Select("""
            SELECT id,
                   title,
                   content,
                   char_count AS charCount,
                   token_count AS tokenCount,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM documents
            WHERE id = #{id}
            """)
    Document findById(@Param("id") Long id);
}
