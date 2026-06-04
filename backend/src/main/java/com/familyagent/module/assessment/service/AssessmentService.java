package com.familyagent.module.assessment.service;

import com.familyagent.module.assessment.entity.AbilityProfile;
import com.familyagent.module.assessment.entity.TestRecord;
import com.familyagent.module.assessment.repository.AbilityProfileRepository;
import com.familyagent.module.assessment.repository.TestRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评估服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final TestRecordRepository testRecordRepository;
    private final AbilityProfileRepository abilityProfileRepository;

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
     * 更新学力档案（答题后的BKT更新）
     */
    public void updateProfile(Long userId, Long kpId, boolean isCorrect) {
        AbilityProfile profile = abilityProfileRepository.findByUserAndKp(userId, kpId);

        if (profile == null) {
            // 初始化档案
            profile = new AbilityProfile();
            profile.setUserId(userId);
            profile.setKpId(kpId);
            profile.setMasteryProbability(0.5);
            profile.setTotalAttempts(0);
            profile.setCorrectAttempts(0);
            profile.setConsecutiveCorrect(0);
        }

        // BKT 简化更新
        double priorMastery = profile.getMasteryProbability();
        double pLearn = 0.15;
        double pGuess = 0.20;
        double pSlip = 0.10;
        double pForget = 0.03;

        double posterior;
        if (isCorrect) {
            double numerator = priorMastery * (1 - pSlip);
            double denominator = numerator + (1 - priorMastery) * pGuess;
            posterior = numerator / denominator;
        } else {
            double numerator = priorMastery * pSlip;
            double denominator = numerator + (1 - priorMastery) * (1 - pGuess);
            posterior = numerator / denominator;
        }

        posterior *= (1 - pForget);
        posterior = Math.min(0.99, Math.max(0.01, posterior));

        profile.setMasteryProbability(posterior);
        profile.setTotalAttempts(profile.getTotalAttempts() + 1);
        if (isCorrect) {
            profile.setCorrectAttempts(profile.getCorrectAttempts() + 1);
            profile.setConsecutiveCorrect(profile.getConsecutiveCorrect() + 1);
        } else {
            profile.setConsecutiveCorrect(0);
        }

        if (profile.getId() == null) {
            abilityProfileRepository.insert(profile);
        } else {
            abilityProfileRepository.updateById(profile);
        }
        log.debug("学力更新: userId={}, kpId={}, correct={}, mastery={}",
                userId, kpId, isCorrect, String.format("%.3f", posterior));
    }
}
