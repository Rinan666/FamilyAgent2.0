package com.familyagent.module.question.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.module.question.dto.CreateQuestionRequest;
import com.familyagent.module.question.entity.KnowledgePoint;
import com.familyagent.module.question.entity.Question;
import com.familyagent.module.question.repository.KnowledgePointRepository;
import com.familyagent.module.question.repository.QuestionRepository;
import com.familyagent.module.user.entity.User;
import com.familyagent.module.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository kpRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // ========== 知识点 ==========

    public List<KnowledgePoint> getKnowledgeTree() {
        List<KnowledgePoint> points = kpRepository.selectList(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .orderByAsc(KnowledgePoint::getLevel)
                        .orderByAsc(KnowledgePoint::getSortOrder)
                        .orderByAsc(KnowledgePoint::getId));
        Map<Long, KnowledgePoint> byId = new LinkedHashMap<>();
        for (KnowledgePoint point : points) {
            point.setChildren(new ArrayList<>());
            byId.put(point.getId(), point);
        }

        List<KnowledgePoint> roots = new ArrayList<>();
        for (KnowledgePoint point : points) {
            if (point.getParentId() == null || !byId.containsKey(point.getParentId())) {
                roots.add(point);
            } else {
                byId.get(point.getParentId()).getChildren().add(point);
            }
        }
        return roots;
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

    public Page<Question> listQuestions(
            int page,
            int size,
            String subject,
            String grade,
            Long kpId,
            List<Long> kpIds,
            Integer difficulty,
            String type,
            String tag) {
        Page<Question> pageParam = new Page<>(page, size);
        boolean hasKpIds = kpIds != null && !kpIds.isEmpty();
        return questionRepository.selectPage(pageParam,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                        .eq(Question::getStatus, "ACTIVE")
                        .and(q -> q.eq(Question::getVisibility, "PUBLIC").or().isNull(Question::getFamilyId))
                        .eq(subject != null, Question::getSubject, subject)
                        .eq(grade != null && !grade.isBlank(), Question::getGrade, grade)
                        .eq(kpId != null, Question::getKpId, kpId)
                        .in(kpId == null && hasKpIds, Question::getKpId, kpIds)
                        .eq(difficulty != null, Question::getDifficulty, difficulty)
                        .eq(type != null && !type.isBlank(), Question::getType, type)
                        .apply(tag != null && !tag.isBlank(), "{0} = ANY(tags)", tag)
                        .orderByDesc(Question::getCreatedAt));
    }

    /**
     * 根据条件抽取测试题
     */
    public List<Question> selectForTest(Long kpId, String subject, Integer difficulty, String type, int limit) {
        return questionRepository.selectForTest(kpId, subject, difficulty, type, Math.min(limit, 20));
    }

    /**
     * Adaptive selection based on legacy local answer statistics.
     */
    public List<Question> selectAdaptive(Long userId, int limit) {
        List<Question> questions = questionRepository.selectAdaptive(userId, limit);
        // Fall back to random selection when historical performance data is insufficient.
        if (questions.isEmpty()) {
            questions = questionRepository.selectForTest(null, "math", null, null, limit);
        }
        return questions;
    }

    public void deleteQuestion(Long id) {
        requireQuestionMaintainer();
        Question question = questionRepository.selectById(id);
        if (question == null || !"ACTIVE".equals(question.getStatus())) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        Question update = new Question();
        update.setId(id);
        update.setStatus("DELETED");
        questionRepository.updateById(update);
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
        insertQuestion(question, parseTags(question.getTags()));
        return question;
    }

    public Question createQuestion(CreateQuestionRequest request) {
        Question question = new Question();
        question.setKpId(request.getKpId());
        question.setSubject(request.getSubject());
        question.setGrade(request.getGrade());
        question.setType(request.getType());
        question.setDifficulty(request.getDifficulty());
        question.setContent(request.getContent());
        question.setAnswer(request.getAnswer());
        question.setTags(toTagArray(safeTags(request.getTags())));
        question.setSource(request.getSource() == null || request.getSource().isBlank() ? "MANUAL" : request.getSource());
        question.setStatus("ACTIVE");
        question.setVisibility("PUBLIC");
        insertQuestion(question, safeTags(request.getTags()));
        return question;
    }

    /**
     * 批量保存题目
     */
    public void batchSaveQuestions(List<Question> questions) {
        for (Question q : questions) {
            insertQuestion(q, parseTags(q.getTags()));
        }
    }

    public List<Question> batchCreateQuestions(List<CreateQuestionRequest> requests) {
        List<Question> questions = new ArrayList<>();
        for (CreateQuestionRequest request : requests) {
            questions.add(createQuestion(request));
        }
        return questions;
    }

    private void requireQuestionMaintainer() {
        Long userId = com.familyagent.common.security.CurrentUserGuard.currentUserId();
        User user = userRepository.selectById(userId);
        String role = user == null ? "" : user.getRole();
        if (!"ADMIN".equalsIgnoreCase(role) && !"OWNER".equalsIgnoreCase(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有管理员可以维护题库");
        }
    }

    private void insertQuestion(Question question, List<String> tags) {
        try {
            String contentJson = objectMapper.writeValueAsString(question.getContent());
            String answerJson = objectMapper.writeValueAsString(question.getAnswer());
            String permissionScopeJson = objectMapper.writeValueAsString(
                    question.getPermissionScope() == null ? java.util.Map.of() : question.getPermissionScope());
            questionRepository.insertQuestion(question, contentJson, answerJson, permissionScopeJson, tags);
        } catch (JsonProcessingException e) {
            log.error("Serialize question failed: subject={}, type={}", question.getSubject(), question.getType(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "题目数据格式不正确");
        }
    }

    private List<String> safeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .toList();
    }

    private List<String> parseTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return List.of();
        }
        return Arrays.stream(tags)
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String[] toTagArray(List<String> tags) {
        return tags.toArray(String[]::new);
    }
}
