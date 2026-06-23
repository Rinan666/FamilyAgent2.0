package com.familyagent.infra.wechat;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * WeChat mini app login client.
 */
@Slf4j
@Component
public class WeChatMiniAppClient {

    private static final String JSCODE_TO_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String JSCODE_TO_SESSION_QUERY_TEMPLATE =
            "?appid={appid}&secret={secret}&js_code={jsCode}&grant_type=authorization_code";
    private static final Pattern WECHAT_LOGIN_CODE_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,512}");

    private final RestTemplate restTemplate;
    private final String appId;
    private final String appSecret;
    private final String sessionUrlTemplate;

    @Autowired
    public WeChatMiniAppClient(@Qualifier("wechatMiniAppRestTemplate") RestTemplate restTemplate,
                               @Value("${wechat.mini-app.app-id:}") String appId,
                               @Value("${wechat.mini-app.app-secret:}") String appSecret) {
        this(restTemplate, appId, appSecret, JSCODE_TO_SESSION_URL);
    }

    WeChatMiniAppClient(RestTemplate restTemplate, String appId, String appSecret, String sessionUrl) {
        this.restTemplate = restTemplate;
        this.appId = appId == null ? "" : appId.trim();
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        this.sessionUrlTemplate = buildSessionUrlTemplate(sessionUrl);
    }

    public SessionInfo exchangeCodeForSession(String code) {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "WeChat login is not configured");
        }

        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "WeChat login code is required");
        }
        if (!WECHAT_LOGIN_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "WeChat login code format is invalid");
        }

        WeChatSessionResponse response;
        try {
            response = restTemplate.getForObject(sessionUrlTemplate, WeChatSessionResponse.class,
                    appId, appSecret, normalizedCode);
        } catch (RestClientException ex) {
            log.warn("WeChat login request failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "WeChat login failed, please retry");
        }

        if (response == null) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "WeChat login failed, please retry");
        }

        if (response.errorCode() != null && response.errorCode() != 0) {
            String errorMessage = response.normalizedErrorMessage();
            log.warn("WeChat login rejected: errcode={}, errmsg={}", response.errorCode(), errorMessage);
            throw new BusinessException(ErrorCode.LOGIN_FAILED,
                    errorMessage.isEmpty() ? "WeChat login failed, please retry" : "WeChat login failed: " + errorMessage);
        }

        String openId = response.normalizedOpenId();
        if (openId.isEmpty()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "WeChat login did not return an openid");
        }

        String sessionKey = response.normalizedSessionKey();
        return new SessionInfo(openId, sessionKey);
    }

    private static String buildSessionUrlTemplate(String sessionUrl) {
        URI uri = URI.create(sessionUrl);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("WeChat session URL must be an absolute URL without query or fragment");
        }
        return uri.toString() + JSCODE_TO_SESSION_QUERY_TEMPLATE;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WeChatSessionResponse(
            @JsonProperty("openid") String openId,
            @JsonProperty("session_key") String sessionKey,
            @JsonProperty("errcode") Integer errorCode,
            @JsonProperty("errmsg") String errorMessage) {

        String normalizedOpenId() {
            return openId == null ? "" : openId.trim();
        }

        String normalizedSessionKey() {
            return sessionKey == null ? "" : sessionKey.trim();
        }

        String normalizedErrorMessage() {
            return errorMessage == null ? "" : errorMessage.trim();
        }
    }

    public record SessionInfo(String openId, String sessionKey) {}
}
