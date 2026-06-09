package com.familyagent.module.family.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FamilyLifecycleStartupRunner implements ApplicationRunner {

    private final FamilyLifecycleService familyLifecycleService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            familyLifecycleService.auditHistoricalFamilyStates();
        } catch (Exception ex) {
            log.error("Failed to audit historical family lifecycle state at startup", ex);
        }
    }
}
