package com.familyagent.module.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.media.entity.MediaAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MediaAttachmentMapper extends BaseMapper<MediaAttachment> {

    @Select("""
        SELECT * FROM media_attachments
        WHERE record_type = #{recordType}
          AND record_id = #{recordId}
        ORDER BY created_at DESC
        """)
    List<MediaAttachment> selectByRecord(
            @Param("recordType") String recordType,
            @Param("recordId") Long recordId);
}
