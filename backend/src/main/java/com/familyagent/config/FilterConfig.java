package com.familyagent.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet filter registration via {@link FilterRegistrationBean} so that
 * filters are fully managed by the Spring Boot servlet container lifecycle.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<ResponseHeaderFilter> responseHeaderFilterRegistration() {
        FilterRegistrationBean<ResponseHeaderFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ResponseHeaderFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("responseHeaderFilter");
        return registration;
    }
}
