"""
Token verification helpers backed by the Java service.

These dependencies protect FastAPI routes with the backend Sa-Token session.
"""
import logging
import secrets
from typing import Optional

import httpx
from fastapi import Header, HTTPException, Request

from app.config import settings

logger = logging.getLogger("familyagent.ai.middleware.auth")

# Bypass Windows system proxy when calling the local backend.
# trust_env=False prevents httpx from reading HTTP_PROXY / system proxy settings.
_NO_PROXY_CLIENT_ARGS = {"trust_env": False, "timeout": 5.0}


def _backend_unavailable_error() -> HTTPException:
    return HTTPException(status_code=503, detail="认证服务暂时不可用，请稍后再试")


async def verify_token(
    request: Request,
    authorization: Optional[str] = Header(None),
) -> dict:
    """
    Validate the Authorization token from the current request.
    Uses the Java backend /api/users/me endpoint as the source of truth.

    Returns:
        dict: The authenticated user payload.

    Raises:
        HTTPException: Raised when the token is missing or invalid.
    """
    if not authorization:
        raise HTTPException(status_code=401, detail="未提供认证令牌")

    user = await _call_backend_verify(authorization)
    if user is None:
        raise HTTPException(status_code=401, detail="认证令牌无效或已过期")

    # Expose the verified user to downstream handlers.
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


def require_internal_service(request: Request) -> None:
    """Reject user-authenticated calls to backend-owned AI capabilities."""
    if not getattr(request.state, "internal_service", False):
        raise HTTPException(status_code=403, detail="Backend service identity is required")


async def _call_backend_verify(token: str) -> Optional[dict]:
    """Call the Java backend /api/users/me endpoint to verify the token."""
    backend_url = settings.backend_url.rstrip("/")
    try:
        async with httpx.AsyncClient(**_NO_PROXY_CLIENT_ARGS) as client:
            resp = await client.get(
                f"{backend_url}/api/users/me",
                headers={"Authorization": token},
            )
            if resp.status_code == 200:
                data = resp.json()
                result_code = data.get("code")
                if result_code == 200:
                    return data.get("data")
                if result_code == 401:
                    logger.warning("Token verification failed: result_code=401")
                    return None
            if resp.status_code == 401:
                # The backend confirmed the token is invalid.
                logger.warning("Token verification failed: status=401")
                return None
            # Other backend failures follow the configured fail-open / fail-closed mode.
            return _handle_backend_unavailable(f"status={resp.status_code}", -1, "backend_error", "后端异常-开发放行")
    except httpx.TimeoutException:
        return _handle_backend_unavailable("timeout", -2, "timeout", "后端超时-开发放行")
    except Exception as error:
        return _handle_backend_unavailable(
            f"errorType={type(error).__name__}",
            -3,
            "error",
            "验证异常-开发放行",
        )


def _handle_backend_unavailable(reason: str, user_id: int, username: str, nickname: str) -> dict:
    """Handle backend verification outages with explicit fail-open/fail-closed behavior."""
    if settings.auth_fail_open_enabled:
        logger.warning("Token verification backend unavailable: %s, AUTH_FAIL_OPEN=true, allowing development fallback", reason)
        return {"id": user_id, "username": username, "nickname": nickname}

    logger.warning("Token verification backend unavailable: %s, AUTH_FAIL_OPEN=false, rejecting request", reason)
    raise _backend_unavailable_error()
