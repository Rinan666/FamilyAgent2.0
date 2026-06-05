package com.familyagent.module.assessment.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.familyagent.module.assessment.entity.WrongQuestionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 错题记录数据访问。
 */
@Mapper
public interface WrongQuestionRecordRepository extends BaseMapper<WrongQuestionRecord> {

    @Insert("""
        INSERT INTO wrong_question_records
        (user_id, family_id, test_record_id, question_id, kp_id, student_answer, score, correct,
         error_type, feedback, parent_explanation, next_suggestion, status)
        VALUES
        (#{record.userId}, #{record.familyId}, #{record.testRecordId}, #{record.questionId}, #{record.kpId},
         #{record.studentAnswer}, #{record.score}, COALESCE(#{record.correct}, false),
         #{record.errorType}, #{record.feedback}, #{record.parentExplanation}, #{record.nextSuggestion},
         COALESCE(#{record.status}, 'OPEN'))
        ON CONFLICT (test_record_id, question_id) DO UPDATE SET
            student_answer = EXCLUDED.student_answer,
            score = EXCLUDED.score,
            correct = EXCLUDED.correct,
            error_type = EXCLUDED.error_type,
            feedback = EXCLUDED.feedback,
            parent_explanation = EXCLUDED.parent_explanation,
            next_suggestion = EXCLUDED.next_suggestion,
            status = EXCLUDED.status,
            updated_at = NOW()
        """)
    @Options(useGeneratedKeys = true, keyProperty = "record.id")
    int insertOrUpdate(@Param("record") WrongQuestionRecord record);

    @Select("""
        SELECT * FROM wrong_question_records
        WHERE user_id = #{userId}
        AND status = 'OPEN'
        ORDER BY created_at DESC
        LIMIT #{limit}
        """)
    List<WrongQuestionRecord> findOpenByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
        SELECT * FROM wrong_question_records
        WHERE test_record_id = #{testRecordId}
        ORDER BY id ASC
        """)
    List<WrongQuestionRecord> findByTestRecordId(@Param("testRecordId") Long testRecordId);
}
