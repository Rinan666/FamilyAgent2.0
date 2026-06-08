package com.familyagent.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class ResponseHeaderFilter implements Filter {

    private static final String NO_STORE = "no-store, max-age=0, must-revalidate";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
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
