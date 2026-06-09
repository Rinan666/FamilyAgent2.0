"""
Token 验证 — 调用 Java 后端验证 Sa-Token

作为 FastAPI Dependency 注入到需要鉴权的路由中。
"""
import logging
import secrets
from typing import Optional

import httpx
from fastapi import Header, HTTPException, Request

from app.config import settings

logger = logging.getLogger("familyagent.ai.middleware.auth")


def _backend_unavailable_error() -> HTTPException:
    return HTTPException(status_code=503, detail="认证服务暂时不可用，请稍后再试")


async def verify_token(
    request: Request,
    authorization: Optional[str] = Header(None),
) -> dict:
    """
    验证请求中的 Authorization token。
    调用 Java 后端 /api/users/me 确认 token 有效。

    Returns:
        dict: 已验证的用户信息

    Raises:
        HTTPException: 401 如果 token 无效或缺失
    """
    if not authorization:
        raise HTTPException(status_code=401, detail="未提供认证令牌")

    user = await _call_backend_verify(authorization)
    if user is None:
        raise HTTPException(status_code=401, detail="认证令牌无效或已过期")

    # 注入到 request state 供下游使用
    request.state.user = user
    request.state.user_id = user.get("id")

    return user


async def verify_token_or_internal_service(
    request: Request,
    authorization: Optional[str] = Header(None),
    internal_service_token: Optional[str] = Header(None, alias="X-Internal-Service-Token"),
) -> dict:
    """Allow normal user auth or trusted Java backend service-to-service calls."""
    if internal_service_token:
        expected = settings.internal_service_token
        if expected and secrets.compare_digest(internal_service_token, expected):
            user = {"id": -100, "username": "internal-service", "nickname": "Backend Service"}
            request.state.user = user
            request.state.user_id = user["id"]
            request.state.internal_service = True
            return user
        raise HTTPException(status_code=401, detail="内部服务令牌无效")

    return await verify_token(request, authorization=authorization)


async def _call_backend_verify(token: str) -> Optional[dict]:
    """调用 Java 后端 /api/users/me 验证 token"""
    backend_url = settings.backend_url.rstrip("/")
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            resp = await client.get(
                f"{backend_url}/api/users/me",
                headers={"Authorization": token},
            )
            if resp.status_code == 200:
                data = resp.json()
                if data.get("code") == 200:
                    return data["data"]
            if resp.status_code == 401:
                # Token 确实无效
                logger.warning("Token 验证失败: status=401")
                return None
            # 其它错误（500等）→ 按环境配置 fail-open / fail-closed
            return _handle_backend_unavailable(f"status={resp.status_code}", -1, "backend_error", "后端异常-开发放行")
    except httpx.TimeoutException:
        return _handle_backend_unavailable("timeout", -2, "timeout", "后端超时-开发放行")
    except Exception as e:
        return _handle_backend_unavailable(f"exception={e}", -3, "error", "验证异常-开发放行")


def _handle_backend_unavailable(reason: str, user_id: int, username: str, nickname: str) -> dict:
    """Handle backend verification outages with explicit fail-open/fail-closed behavior."""
    if settings.auth_fail_open_enabled:
        logger.warning("Token 验证后端不可用: %s, AUTH_FAIL_OPEN=true, 开发放行", reason)
        return {"id": user_id, "username": username, "nickname": nickname}

    logger.warning("Token 验证后端不可用: %s, AUTH_FAIL_OPEN=false, 拒绝请求", reason)
    raise _backend_unavailable_error()
