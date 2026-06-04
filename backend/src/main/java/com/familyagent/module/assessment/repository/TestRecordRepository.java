package com.familyagent.module.assessment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.assessment.entity.TestRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 测试记录数据访问
 */
@Mapper
public interface TestRecordRepository extends BaseMapper<TestRecord> {

    @Insert("""
        <script>
        INSERT INTO test_records
        (user_id, family_id, question_ids, answers, scores, time_spent, total_score, total_time, status, source,
         visibility, permission_scope)
        VALUES
        (
            #{record.userId},
            #{record.familyId},
            ARRAY[
                <foreach collection='questionIds' item='id' separator=','>
                    #{id}
                </foreach>
            ]::bigint[],
            CAST(#{answersJson} AS jsonb),
            CAST(#{scoresJson} AS jsonb),
            ARRAY[
                <foreach collection='timeSpent' item='time' separator=','>
                    #{time}
                </foreach>
            ]::integer[],
            #{record.totalScore},
            #{record.totalTime},
            #{record.status},
            #{record.source},
            COALESCE(#{record.visibility}, 'PRIVATE'),
            CAST(#{permissionScopeJson} AS jsonb)
        )
        </script>
        """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id")
    int insertSubmitted(
            @Param("record") TestRecord record,
            @Param("questionIds") List<Long> questionIds,
            @Param("answersJson") String answersJson,
            @Param("scoresJson") String scoresJson,
            @Param("permissionScopeJson") String permissionScopeJson,
            @Param("timeSpent") List<Integer> timeSpent);

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
