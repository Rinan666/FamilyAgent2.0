package com.familyagent.module.invite.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.invite.entity.InviteCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 邀请码数据访问。
 */
@Mapper
public interface InviteCodeRepository extends BaseMapper<InviteCode> {

    @Select("""
        SELECT id, code, source, description, max_uses, used_count, status, expires_at,
               created_by, created_at, updated_at
        FROM invite_codes
        WHERE code = #{code}
        """)
    InviteCode findByCode(String code);

    @Update("""
        UPDATE invite_codes
        SET used_count = used_count + 1,
            updated_at = NOW()
        WHERE id = #{id}
          AND status = 'ACTIVE'
          AND (expires_at IS NULL OR expires_at > NOW())
          AND (max_uses IS NULL OR used_count < max_uses)
        """)
    int incrementUsedCount(Long id);

    @Update("""
        UPDATE invite_codes
        SET used_count = used_count + 1,
            updated_at = NOW()
        WHERE UPPER(code) = UPPER(#{code})
          AND status = 'ACTIVE'
          AND (expires_at IS NULL OR expires_at > NOW())
          AND (max_uses IS NULL OR used_count < max_uses)
        """)
    int incrementUsedCountByCode(@Param("code") String code);
}
