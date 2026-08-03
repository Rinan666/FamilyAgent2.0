package com.familyagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    @Test
    void shouldProtectApiAndActuatorEndpoints() {
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(any())).thenReturn(registration);
        when(registration.addPathPatterns("/api/**", "/actuator/**")).thenReturn(registration);

        new SecurityConfig().addInterceptors(registry);

        verify(registration).addPathPatterns("/api/**", "/actuator/**");
        verify(registration).excludePathPatterns(
                "/api/users/register",
                "/api/users/login",
                "/docs/**",
                "/v3/api-docs/**",
                "/actuator/health"
        );
    }

    @Test
    void productionProfileShouldDisableSwagger() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application-prod",
                new ClassPathResource("application-prod.yml")
        );

        assertEquals(false, property(sources, "springdoc.api-docs.enabled"));
        assertEquals(false, property(sources, "springdoc.swagger-ui.enabled"));
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
