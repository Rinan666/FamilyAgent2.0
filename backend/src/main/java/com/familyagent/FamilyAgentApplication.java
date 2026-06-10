package com.familyagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main application entry point for FamilyAgent.
 *
 * @author FamilyAgent Team
 * @since 0.1.0
 */
@EnableAsync
@SpringBootApplication
public class FamilyAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyAgentApplication.class, args);
    }
}
