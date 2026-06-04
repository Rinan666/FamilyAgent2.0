package com.familyagent.module.assessment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.assessment.entity.TestRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 测试记录数据访问
 */
@Mapper
public interface TestRecordRepository extends BaseMapper<TestRecord> {

    @Select("SELECT * FROM test_records WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    List<TestRecord> findByUserId(Long userId, int limit);

    @Select("""
        SELECT * FROM test_records
        WHERE user_id = #{userId}
        AND created_at > NOW() - INTERVAL '30 days'
        ORDER BY created_at DESC
        """)
    List<TestRecord> findRecentByUserId(Long userId);
}
