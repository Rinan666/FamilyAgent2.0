package com.familyagent.infra.wechat;

import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WeChat mini app login client.
 */
@Slf4j
@Component
public class WeChatMiniAppClient {

    private static final String JSCODE_TO_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    private final RestTemplate restTemplate;
    private final String appId;
    private final String appSecret;
    private final String sessionUrl;

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
        this.sessionUrl = sessionUrl;
    }

    public SessionInfo exchangeCodeForSession(String code) {
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "WeChat login is not configured");
        }

        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "WeChat login code is required");
        }

        String uri = UriComponentsBuilder.fromHttpUrl(sessionUrl)
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .queryParam("js_code", normalizedCode)
                .queryParam("grant_type", "authorization_code")
                .build(true)
                .toUriString();

        Map<String, Object> response;
        try {
            response = restTemplate.getForObject(uri, Map.class);
        } catch (RestClientException ex) {
            log.warn("WeChat login request failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "WeChat login failed, please retry");
        }

        if (response == null) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "WeChat login failed, please retry");
        }

        Number errorCode = response.get("errcode") instanceof Number number ? number : null;
        if (errorCode != null && errorCode.intValue() != 0) {
            String errorMessage = response.get("errmsg") instanceof String text ? text.trim() : "";
            log.warn("WeChat login rejected: errcode={}, errmsg={}", errorCode, errorMessage);
            throw new BusinessException(ErrorCode.LOGIN_FAILED,
                    errorMessage.isEmpty() ? "WeChat login failed, please retry" : "WeChat login failed: " + errorMessage);
        }

        String openId = response.get("openid") instanceof String text ? text.trim() : "";
        if (openId.isEmpty()) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED, "WeChat login did not return an openid");
        }

        String sessionKey = response.get("session_key") instanceof String text ? text.trim() : "";
        return new SessionInfo(openId, sessionKey);
    }

    public record SessionInfo(String openId, String sessionKey) {}
}
