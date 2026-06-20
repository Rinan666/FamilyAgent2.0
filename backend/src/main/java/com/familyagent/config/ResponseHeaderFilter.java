package com.familyagent.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Writes common response headers (X-Content-Type-Options, charset, Cache-Control).
 * <p>
 * Registered explicitly via {@link FilterRegistrationBean} in {@link FilterConfig}
 * rather than as a generic {@code @Component}, so that its servlet lifecycle
 * (URL patterns, order) is declared in one place.
 */
public class ResponseHeaderFilter implements Filter {

    private static final String NO_STORE = "no-store, max-age=0, must-revalidate";
    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'";
    private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        httpResponse.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String path = httpRequest.getRequestURI();
        if (path.startsWith("/api/") || path.startsWith("/actuator/")) {
            httpResponse.setHeader("Cache-Control", NO_STORE);
        }

        chain.doFilter(request, response);

        String contentType = httpResponse.getContentType();
        if (!httpResponse.isCommitted()
                && contentType != null
                && isJsonContentType(contentType)
                && !contentType.toLowerCase(Locale.ROOT).contains("charset=")) {
            httpResponse.setContentType(contentType + ";charset=UTF-8");
        }
    }

    private boolean isJsonContentType(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("application/json") || normalized.contains("+json");
    }
}
