package com.familyagent.module.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.familyagent.module.question.entity.Question;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 题目数据访问
 */
@Mapper
public interface QuestionRepository extends BaseMapper<Question> {

    @Insert("""
        <script>
        INSERT INTO questions
        (family_id, kp_id, subject, grade, type, difficulty, content, answer, tags, source, status, visibility,
         permission_scope, created_by, reviewed_by)
        VALUES
        (
            #{record.familyId},
            #{record.kpId},
            #{record.subject},
            #{record.grade},
            #{record.type},
            #{record.difficulty},
            CAST(#{contentJson} AS jsonb),
            CAST(#{answerJson} AS jsonb),
            <choose>
                <when test='tags != null and tags.size() > 0'>
                    ARRAY[
                        <foreach collection='tags' item='tag' separator=','>
                            #{tag}
                        </foreach>
                    ]::text[]
                </when>
                <otherwise>
                    ARRAY[]::text[]
                </otherwise>
            </choose>,
            #{record.source},
            #{record.status},
            COALESCE(#{record.visibility}, 'PUBLIC'),
            CAST(#{permissionScopeJson} AS jsonb),
            #{record.createdBy},
            #{record.reviewedBy}
        )
        </script>
        """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id")
    int insertQuestion(
            @Param("record") Question record,
            @Param("contentJson") String contentJson,
            @Param("answerJson") String answerJson,
            @Param("permissionScopeJson") String permissionScopeJson,
            @Param("tags") List<String> tags);

    @Select("""
        <script>
        SELECT * FROM questions
        WHERE status = 'ACTIVE'
        AND (visibility = 'PUBLIC' OR family_id IS NULL)
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
        WHERE status = 'ACTIVE'
        AND (visibility = 'PUBLIC' OR family_id IS NULL)
        AND kp_id = ANY(
            SELECT kp_id FROM ability_profiles
            WHERE user_id = #{userId} AND mastery_probability BETWEEN 0.3 AND 0.7
        )
        ORDER BY RANDOM() LIMIT #{limit}
        """)
    List<Question> selectAdaptive(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
        SELECT DISTINCT q.*
        FROM questions q
        JOIN test_records tr ON tr.user_id = #{userId}
        JOIN LATERAL jsonb_each_text(COALESCE(tr.scores, '{}'::jsonb)) score(question_id, score_value) ON true
        WHERE q.status = 'ACTIVE'
        AND (q.visibility = 'PUBLIC' OR q.family_id IS NULL)
        AND score.question_id ~ '^[0-9]+$'
        AND score.score_value ~ '^-?[0-9]+(\\.[0-9]+)?$'
        AND q.id = score.question_id::bigint
        AND score.score_value::numeric < 60
        AND tr.id IN (
            SELECT id FROM test_records
            WHERE user_id = #{userId}
            ORDER BY created_at DESC
            LIMIT 100
        )
        ORDER BY q.id DESC
        LIMIT #{limit}
        """)
    List<Question> findWrongQuestions(@Param("userId") Long userId, @Param("limit") int limit);
}
