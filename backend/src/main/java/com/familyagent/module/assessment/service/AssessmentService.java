package com.familyagent.module.assessment.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.assessment.dto.SubmitTestRequest;
import com.familyagent.module.assessment.entity.AbilityProfile;
import com.familyagent.module.assessment.entity.TestRecord;
import com.familyagent.module.assessment.repository.AbilityProfileRepository;
import com.familyagent.module.assessment.repository.TestRecordRepository;
import com.familyagent.module.family.service.FamilyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AbilityProfileRepository abilityProfileRepository;
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
        return testRecordRepository.findByUserId(userId, limit);
    }

    /**
     * 获取近期测试记录（30天）
     */
    public List<TestRecord> getRecentTests(Long userId) {
        return testRecordRepository.findRecentByUserId(userId);
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

        IntStream.range(0, request.getResults().size()).forEach(i -> {
            SubmitTestRequest.TestQuestionResult result = request.getResults().get(i);
            boolean correct = Boolean.TRUE.equals(result.getCorrect())
                    || (result.getScore() != null && result.getScore() >= 60.0);
            updateProfile(request.getUserId(), result.getKpId(), correct);
        });

        return record;
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
}
