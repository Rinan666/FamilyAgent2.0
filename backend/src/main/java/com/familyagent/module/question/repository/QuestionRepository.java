package com.familyagent.module.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.familyagent.module.question.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目数据访问
 */
@Mapper
public interface QuestionRepository extends BaseMapper<Question> {

    @Select("""
        <script>
        SELECT * FROM questions
        WHERE status = 'ACTIVE'
        <if test='kpId != null'> AND kp_id = #{kpId}</if>
        <if test='subject != null'> AND subject = #{subject}</if>
        <if test='difficulty != null'> AND difficulty = #{difficulty}</if>
        <if test='type != null'> AND type = #{type}</if>
        ORDER BY usage_count ASC, correct_rate ASC
        LIMIT #{limit}
        </script>
        """)
    List<Question> selectForTest(@Param("kpId") Long kpId,
                                  @Param("subject") String subject,
                                  @Param("difficulty") Integer difficulty,
                                  @Param("type") String type,
                                  @Param("limit") int limit);

    @Select("""
        SELECT * FROM questions
        WHERE status = 'ACTIVE' AND kp_id = ANY(
            SELECT kp_id FROM ability_profiles
            WHERE user_id = #{userId} AND mastery_probability BETWEEN 0.3 AND 0.7
        )
        ORDER BY RANDOM() LIMIT #{limit}
        """)
    List<Question> selectAdaptive(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
        SELECT * FROM questions
        WHERE status = 'ACTIVE' AND id IN (
            SELECT UNNEST(question_ids) FROM test_records
            WHERE user_id = #{userId} AND total_score < 60
            ORDER BY created_at DESC LIMIT 100
        )
        LIMIT #{limit}
        """)
    List<Question> findWrongQuestions(@Param("userId") Long userId, @Param("limit") int limit);
}
