package com.familyagent.module.session.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.session.entity.ChatSessionArchive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatSessionArchiveRepository extends BaseMapper<ChatSessionArchive> {

    @Select("""
            SELECT id, session_id, start_seq, end_seq, summary, object_key, message_count, token_count, created_at, metadata
            FROM chat_session_archives
            WHERE session_id = #{sessionId}
            ORDER BY start_seq ASC, id ASC
            """)
    List<ChatSessionArchive> findBySessionId(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT id, session_id, start_seq, end_seq, summary, object_key, message_count, token_count, created_at, metadata
            FROM chat_session_archives
            WHERE session_id = #{sessionId}
              AND start_seq < #{beforeSeq}
            ORDER BY end_seq DESC, id DESC
            LIMIT #{limit}
            """)
    List<ChatSessionArchive> findRangesBeforeSeqDesc(@Param("sessionId") Long sessionId,
                                                     @Param("beforeSeq") long beforeSeq,
                                                     @Param("limit") int limit);
}
