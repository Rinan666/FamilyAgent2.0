package com.familyagent;

import com.familyagent.config.LocalEnvDefaults;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application entry point for FamilyAgent.
 *
 * @author FamilyAgent Team
 * @since 0.1.0
 */
@SpringBootApplication
public class FamilyAgentApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(FamilyAgentApplication.class);
        application.setDefaultProperties(LocalEnvDefaults.load());
        application.run(args);
    }
}
