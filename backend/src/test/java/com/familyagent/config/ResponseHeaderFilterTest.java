package com.familyagent.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponseHeaderFilterTest {

    private final ResponseHeaderFilter filter = new ResponseHeaderFilter();

    @Test
    void doFilter_shouldWriteSecurityHeadersAndNoStoreForApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setContentType("application/json");

        filter.doFilter(request, response, chain);

        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", response.getHeader("X-Frame-Options"));
        assertEquals("default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'",
                response.getHeader("Content-Security-Policy"));
        assertEquals("strict-origin-when-cross-origin", response.getHeader("Referrer-Policy"));
        assertEquals("camera=(), microphone=(), geolocation=(), payment=()",
                response.getHeader("Permissions-Policy"));
        assertEquals("no-store, max-age=0, must-revalidate", response.getHeader("Cache-Control"));
        assertEquals("application/json;charset=UTF-8", response.getContentType());
    }
}
