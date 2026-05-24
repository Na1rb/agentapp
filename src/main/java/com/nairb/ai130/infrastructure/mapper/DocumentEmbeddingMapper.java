package com.nairb.ai130.infrastructure.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DocumentEmbeddingMapper {

    @Select("SELECT COUNT(*) FROM document_embeddings WHERE metadata->>'chat_id' = #{chatId}")
    int countByChatId(String chatId);

    @Delete("DELETE FROM document_embeddings WHERE metadata->>'chat_id' = #{chatId}")
    int deleteByChatId(String chatId);

    @Select("""
            <script>
            SELECT id, text
            FROM document_embeddings
            WHERE text ILIKE CONCAT('%', #{query}, '%')
            <if test="chatId != null and chatId != ''">
                AND metadata->>'chat_id' = #{chatId}
            </if>
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> fuzzySearch(@Param("query") String query,
                                          @Param("chatId") String chatId,
                                          @Param("limit") int limit);
}
