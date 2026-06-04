package com.familyagent.module.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.question.entity.KnowledgePoint;
import com.familyagent.module.question.entity.Question;
import com.familyagent.module.question.repository.KnowledgePointRepository;
import com.familyagent.module.question.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 题库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository kpRepository;

    // ========== 知识点 ==========

    public List<KnowledgePoint> getKnowledgeTree() {
        return kpRepository.findRoots();
    }

    public List<KnowledgePoint> getChildKnowledgePoints(Long parentId) {
        return kpRepository.findByParentId(parentId);
    }

    public List<KnowledgePoint> getKnowledgePointsBySubject(String subject) {
        return kpRepository.findBySubject(subject);
    }

    public KnowledgePoint getKnowledgePoint(Long id) {
        KnowledgePoint kp = kpRepository.selectById(id);
        if (kp == null) {
            throw new BusinessException(ErrorCode.KP_NOT_FOUND);
        }
        return kp;
    }

    // ========== 题目 ==========

    public Question getQuestion(Long id) {
        Question question = questionRepository.selectById(id);
        if (question == null || !"ACTIVE".equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        return question;
    }

    public Page<Question> listQuestions(int page, int size, String subject, Long kpId, Integer difficulty) {
        Page<Question> pageParam = new Page<>(page, size);
        return questionRepository.selectPage(pageParam,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                        .eq(Question::getStatus, "ACTIVE")
                        .eq(subject != null, Question::getSubject, subject)
                        .eq(kpId != null, Question::getKpId, kpId)
                        .eq(difficulty != null, Question::getDifficulty, difficulty)
                        .orderByDesc(Question::getCreatedAt));
    }

    /**
     * 根据条件抽取测试题
     */
    public List<Question> selectForTest(Long kpId, String subject, Integer difficulty, String type, int limit) {
        return questionRepository.selectForTest(kpId, subject, difficulty, type, Math.min(limit, 20));
    }

    /**
     * 自适应抽题：基于学力档案的最近发展区
     */
    public List<Question> selectAdaptive(Long userId, int limit) {
        List<Question> questions = questionRepository.selectAdaptive(userId, limit);
        // 如果BKT数据不足，回退到随机抽题
        if (questions.isEmpty()) {
            questions = questionRepository.selectForTest(null, "math", null, null, limit);
        }
        return questions;
    }

    /**
     * 获取错题
     */
    public List<Question> getWrongQuestions(Long userId, int limit) {
        return questionRepository.findWrongQuestions(userId, limit);
    }

    /**
     * 保存题目（AI生成后入库）
     */
    public Question saveQuestion(Question question) {
        questionRepository.insert(question);
        return question;
    }

    /**
     * 批量保存题目
     */
    public void batchSaveQuestions(List<Question> questions) {
        for (Question q : questions) {
            questionRepository.insert(q);
        }
    }
}
