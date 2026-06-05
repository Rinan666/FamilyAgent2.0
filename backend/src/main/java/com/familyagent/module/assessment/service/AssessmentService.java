package com.familyagent.module.assessment.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.assessment.dto.SubmitTestRequest;
import com.familyagent.module.assessment.dto.TestRecordDetailVO;
import com.familyagent.module.assessment.entity.AbilityProfile;
import com.familyagent.module.assessment.entity.TestRecord;
import com.familyagent.module.assessment.entity.WrongQuestionRecord;
import com.familyagent.module.assessment.repository.AbilityProfileRepository;
import com.familyagent.module.assessment.repository.TestRecordRepository;
import com.familyagent.module.assessment.repository.WrongQuestionRecordRepository;
import com.familyagent.module.family.service.FamilyService;
import com.familyagent.module.question.entity.Question;
import com.familyagent.module.question.repository.QuestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * 评估服务
 * <p>
 * BKT知识追踪由Python AI服务统一计算，Java负责数据持久化。
 * Python是BKT算法的唯一权威来源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final TestRecordRepository testRecordRepository;
    private final WrongQuestionRecordRepository wrongQuestionRecordRepository;
    private final AbilityProfileRepository abilityProfileRepository;
    private final QuestionRepository questionRepository;
    private final AIServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final FamilyService familyService;

    /**
     * 获取用户学力档案
     */
    public List<AbilityProfile> getUserProfiles(Long userId) {
        return abilityProfileRepository.findByUserId(userId);
    }

    /**
     * 获取学力档案Map (kpId -> profile)
     */
    public Map<Long, AbilityProfile> getUserProfileMap(Long userId) {
        return abilityProfileRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(AbilityProfile::getKpId, p -> p));
    }

    /**
     * 获取最近发展区知识点
     */
    public List<AbilityProfile> getZPD(Long userId) {
        return abilityProfileRepository.findZPD(userId, 10);
    }

    /**
     * 获取测试历史
     */
    public List<TestRecord> getTestHistory(Long userId, int limit) {
        return testRecordRepository.findByUserId(userId, limit).stream()
                .map(this::normalizeTestRecord)
                .toList();
    }

    /**
     * 获取近期测试记录（30天）
     */
    public List<TestRecord> getRecentTests(Long userId) {
        return testRecordRepository.findRecentByUserId(userId).stream()
                .map(this::normalizeTestRecord)
                .toList();
    }

    /**
     * 获取测试记录详情。
     */
    public TestRecordDetailVO getTestRecordDetail(Long userId, Long recordId) {
        TestRecord record = testRecordRepository.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "测试记录不存在");
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该测试记录");
        }

        normalizeTestRecord(record);
        List<Long> questionIds = toLongList(record.getQuestionIds());
        Map<String, String> answers = toStringMap(record.getAnswers());
        Map<String, Double> scores = toDoubleMap(record.getScores());
        List<Integer> timeSpent = toIntegerList(record.getTimeSpent());

        Map<Long, Question> questions = new HashMap<>();
        if (!questionIds.isEmpty()) {
            questionRepository.selectBatchIds(questionIds).forEach(question -> questions.put(question.getId(), question));
        }

        Map<Long, WrongQuestionRecord> wrongRecords = new HashMap<>();
        wrongQuestionRecordRepository.findByTestRecordId(recordId)
                .forEach(wrong -> wrongRecords.put(wrong.getQuestionId(), wrong));

        List<TestRecordDetailVO.Item> items = new ArrayList<>();
        for (int i = 0; i < questionIds.size(); i++) {
            Long questionId = questionIds.get(i);
            Question question = questions.get(questionId);
            Double score = scores.getOrDefault(String.valueOf(questionId), 0.0);
            WrongQuestionRecord wrongRecord = wrongRecords.get(questionId);

            TestRecordDetailVO.Item item = new TestRecordDetailVO.Item();
            item.setQuestionId(questionId);
            item.setKpId(question == null ? null : question.getKpId());
            item.setQuestion(question);
            item.setStudentAnswer(answers.getOrDefault(String.valueOf(questionId), ""));
            item.setCorrectAnswer(question == null ? null : question.getAnswer());
            item.setScore(score);
            item.setCorrect(score >= 60.0);
            item.setTimeSpent(i < timeSpent.size() ? timeSpent.get(i) : 0);
            item.setWrong(wrongRecord != null || score < 60.0);
            item.setWrongRecordId(wrongRecord == null ? null : wrongRecord.getId());
            item.setWrongStatus(wrongRecord == null ? null : wrongRecord.getStatus());
            item.setErrorType(wrongRecord == null ? null : wrongRecord.getErrorType());
            item.setFeedback(wrongRecord == null ? null : wrongRecord.getFeedback());
            item.setParentExplanation(wrongRecord == null ? null : wrongRecord.getParentExplanation());
            item.setNextSuggestion(wrongRecord == null ? null : wrongRecord.getNextSuggestion());
            items.add(item);
        }

        TestRecordDetailVO detail = new TestRecordDetailVO();
        detail.setRecord(record);
        detail.setItems(items);
        return detail;
    }

    private TestRecord normalizeTestRecord(TestRecord record) {
        record.setQuestionIds(toLongList(record.getQuestionIds()));
        record.setAnswers(toStringMap(record.getAnswers()));
        record.setScores(toDoubleMap(record.getScores()));
        record.setTimeSpent(toIntegerList(record.getTimeSpent()));
        return record;
    }

    /**
     * 保存测试记录
     */
    public TestRecord saveTestRecord(TestRecord record) {
        insertTestRecord(record);
        return record;
    }

    @SuppressWarnings("unchecked")
    private void insertTestRecord(TestRecord record) {
        List<Long> questionIds = (List<Long>) record.getQuestionIds();
        List<Integer> timeSpent = (List<Integer>) record.getTimeSpent();
        try {
            String answersJson = objectMapper.writeValueAsString(record.getAnswers());
            String scoresJson = objectMapper.writeValueAsString(record.getScores());
            String permissionScopeJson = objectMapper.writeValueAsString(
                    record.getPermissionScope() == null ? Map.of() : record.getPermissionScope());
            testRecordRepository.insertSubmitted(record, questionIds, answersJson, scoresJson, permissionScopeJson, timeSpent);
        } catch (JsonProcessingException e) {
            log.error("Serialize test record failed: userId={}", record.getUserId(), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "测试记录数据格式不正确");
        }
    }

    /**
     * 保存一次独立测试结果，并按题目结果更新 BKT 学力档案。
     */
    @Transactional
    public TestRecord submitTest(SubmitTestRequest request) {
        if (request.getResults() == null || request.getResults().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "测试结果不能为空");
        }
        if (request.getFamilyId() != null) {
            familyService.checkMembership(request.getFamilyId());
        }

        List<Long> questionIds = request.getResults().stream()
                .map(SubmitTestRequest.TestQuestionResult::getQuestionId)
                .toList();

        Map<String, String> answers = request.getResults().stream()
                .collect(Collectors.toMap(
                        r -> String.valueOf(r.getQuestionId()),
                        r -> r.getAnswer() == null ? "" : r.getAnswer(),
                        (a, b) -> b
                ));

        Map<String, Double> scores = request.getResults().stream()
                .collect(Collectors.toMap(
                        r -> String.valueOf(r.getQuestionId()),
                        r -> r.getScore() == null ? 0.0 : r.getScore(),
                        (a, b) -> b
                ));

        List<Integer> timeSpent = request.getResults().stream()
                .map(r -> r.getTimeSpent() == null ? 0 : r.getTimeSpent())
                .toList();

        double totalScore = request.getResults().stream()
                .mapToDouble(r -> r.getScore() == null ? 0.0 : r.getScore())
                .average()
                .orElse(0.0);

        TestRecord record = new TestRecord();
        record.setUserId(request.getUserId());
        record.setFamilyId(request.getFamilyId());
        record.setQuestionIds(questionIds);
        record.setAnswers(answers);
        record.setScores(scores);
        record.setTimeSpent(timeSpent);
        record.setTotalScore(totalScore);
        record.setTotalTime(request.getTotalTime());
        record.setStatus("COMPLETED");
        record.setSource(request.getSource() == null ? "GENERATED_TEST" : request.getSource());
        record.setVisibility("PRIVATE");
        record.setPermissionScope(Map.of());
        insertTestRecord(record);
        saveWrongQuestionRecords(request, record);

        IntStream.range(0, request.getResults().size()).forEach(i -> {
            SubmitTestRequest.TestQuestionResult result = request.getResults().get(i);
            boolean correct = Boolean.TRUE.equals(result.getCorrect())
                    || (result.getScore() != null && result.getScore() >= 60.0);
            updateProfile(request.getUserId(), result.getKpId(), correct);
        });

        return record;
    }

    private void saveWrongQuestionRecords(SubmitTestRequest request, TestRecord record) {
        for (SubmitTestRequest.TestQuestionResult result : request.getResults()) {
            double score = result.getScore() == null ? 0.0 : result.getScore();
            boolean correct = Boolean.TRUE.equals(result.getCorrect()) || score >= 60.0;
            if (correct) {
                continue;
            }

            WrongQuestionRecord wrongRecord = new WrongQuestionRecord();
            wrongRecord.setUserId(request.getUserId());
            wrongRecord.setFamilyId(request.getFamilyId());
            wrongRecord.setTestRecordId(record.getId());
            wrongRecord.setQuestionId(result.getQuestionId());
            wrongRecord.setKpId(result.getKpId());
            wrongRecord.setStudentAnswer(result.getAnswer() == null ? "" : result.getAnswer());
            wrongRecord.setScore(score);
            wrongRecord.setCorrect(false);
            wrongRecord.setErrorType(blankToNull(result.getErrorType()));
            wrongRecord.setFeedback(blankToNull(result.getFeedback()));
            wrongRecord.setParentExplanation(blankToNull(result.getParentExplanation()));
            wrongRecord.setNextSuggestion(blankToNull(result.getNextSuggestion()));
            wrongRecord.setStatus("OPEN");
            wrongQuestionRecordRepository.insertOrUpdate(wrongRecord);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 更新学力档案 — 答题后调用Python BKT引擎更新掌握概率
     * <p>
     * 计算逻辑由Python AI服务的 /ai/assessment/bkt/update 端点提供，
     * Java侧只负责：
     * 1. 计算 days_since_last（距上次答题天数）
     * 2. 调用Python BKT服务
     * 3. 持久化结果到数据库
     */
    @SuppressWarnings("unchecked")
    public void updateProfile(Long userId, Long kpId, boolean isCorrect) {
        AbilityProfile profile = abilityProfileRepository.findByUserAndKp(userId, kpId);

        if (profile == null) {
            profile = new AbilityProfile();
            profile.setUserId(userId);
            profile.setKpId(kpId);
            profile.setMasteryProbability(0.5);
            profile.setTotalAttempts(0);
            profile.setCorrectAttempts(0);
            profile.setConsecutiveCorrect(0);
            profile.setVisibility("PRIVATE");
            profile.setPermissionScope(Map.of());
        }

        // 计算距上次答题天数
        LocalDateTime now = LocalDateTime.now();
        int daysSinceLast = 0;
        if (profile.getLastAttemptAt() != null) {
            daysSinceLast = (int) ChronoUnit.DAYS.between(profile.getLastAttemptAt(), now);
        }

        double priorMastery = profile.getMasteryProbability();

        try {
            // 调用Python BKT引擎（权威计算）
            Map<String, Object> bktResult = aiServiceClient.updateBKT(
                    priorMastery, isCorrect, daysSinceLast);

            double posterior = ((Number) bktResult.get("posterior_mastery")).doubleValue();
            String masteryLevel = (String) bktResult.get("mastery_level");
            boolean fallback = bktResult.getOrDefault("fallback", Boolean.FALSE).equals(Boolean.TRUE);

            profile.setMasteryProbability(posterior);

            if (fallback) {
                log.warn("BKT使用降级计算: userId={}, kpId={}, correct={}, posterior={}",
                        userId, kpId, isCorrect, String.format("%.3f", posterior));
            } else {
                log.debug("BKT更新: userId={}, kpId={}, correct={}, prior={}, posterior={}, level={}",
                        userId, kpId, isCorrect,
                        String.format("%.3f", priorMastery),
                        String.format("%.3f", posterior),
                        masteryLevel);
            }
        } catch (Exception e) {
            log.error("BKT服务不可用, 跳过本次更新: userId={}, kpId={}", userId, kpId, e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "学力评估服务暂不可用");
        }

        // 更新统计数据（Java侧负责）
        profile.setTotalAttempts(profile.getTotalAttempts() + 1);
        profile.setLastAttemptAt(now);
        if (isCorrect) {
            profile.setCorrectAttempts(profile.getCorrectAttempts() + 1);
            profile.setConsecutiveCorrect(profile.getConsecutiveCorrect() + 1);
            profile.setLastCorrectAt(now);
        } else {
            profile.setConsecutiveCorrect(0);
        }

        // 持久化
        if (profile.getId() == null) {
            abilityProfileRepository.insert(profile);
        } else {
            abilityProfileRepository.updateById(profile);
        }
    }

    private List<Long> toLongList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::toLong).filter(v -> v != null).toList();
        }
        if (value.getClass().isArray()) {
            List<Long> result = new ArrayList<>();
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Long item = toLong(java.lang.reflect.Array.get(value, i));
                if (item != null) {
                    result.add(item);
                }
            }
            return result;
        }
        if (value instanceof java.sql.Array sqlArray) {
            try {
                return toLongList(sqlArray.getArray());
            } catch (SQLException e) {
                return List.of();
            }
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isBlank()) {
                return List.of();
            }
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                try {
                    if (trimmed.startsWith("{") && !trimmed.startsWith("{\"")) {
                        return parsePgLongArray(trimmed);
                    }
                    return objectMapper.readValue(trimmed, new TypeReference<List<Long>>() {});
                } catch (JsonProcessingException ignored) {
                    return parsePgLongArray(trimmed);
                }
            }
        }
        Long single = toLong(value);
        return single == null ? List.of() : List.of(single);
    }

    private List<Integer> toIntegerList(Object value) {
        return toLongList(value).stream().map(Long::intValue).toList();
    }

    private Map<String, String> toStringMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
            Map<String, String> result = new HashMap<>();
            raw.forEach((key, item) -> result.put(key, item == null ? "" : String.valueOf(item)));
            return result;
        } catch (IllegalArgumentException e) {
            if (value instanceof String text) {
                try {
                    return objectMapper.readValue(text, new TypeReference<Map<String, String>>() {});
                } catch (JsonProcessingException ignored) {
                    return Collections.emptyMap();
                }
            }
            return Collections.emptyMap();
        }
    }

    private Map<String, Double> toDoubleMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() {});
            Map<String, Double> result = new HashMap<>();
            raw.forEach((key, item) -> result.put(key, toDouble(item)));
            return result;
        } catch (IllegalArgumentException e) {
            if (value instanceof String text) {
                try {
                    Map<String, Object> raw = objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
                    Map<String, Double> result = new HashMap<>();
                    raw.forEach((key, item) -> result.put(key, toDouble(item)));
                    return result;
                } catch (JsonProcessingException ignored) {
                    return Collections.emptyMap();
                }
            }
            return Collections.emptyMap();
        }
    }

    private List<Long> parsePgLongArray(String text) {
        String normalized = text.replace("{", "").replace("}", "").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (String part : normalized.split(",")) {
            Long item = toLong(part.trim().replace("\"", ""));
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
