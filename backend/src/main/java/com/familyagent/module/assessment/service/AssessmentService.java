package com.familyagent.module.assessment.service;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.familyagent.infra.ai.AIServiceClient;
import com.familyagent.module.assessment.entity.AbilityProfile;
import com.familyagent.module.assessment.entity.TestRecord;
import com.familyagent.module.assessment.repository.AbilityProfileRepository;
import com.familyagent.module.assessment.repository.TestRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
        testRecordRepository.insert(record);
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
