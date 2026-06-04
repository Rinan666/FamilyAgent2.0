"""
Token 验证 — 调用 Java 后端验证 Sa-Token

作为 FastAPI Dependency 注入到需要鉴权的路由中。
"""
import logging
from typing import Optional

import httpx
from fastapi import Header, HTTPException, Request

from app.config import settings

logger = logging.getLogger("familyagent.ai.middleware.auth")


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
            # 其它错误（500等）→ 认为是后端问题，不拦截
            logger.warning(f"Token 验证后端异常: status={resp.status_code}, 放行")
            return {"id": -1, "username": "unknown", "nickname": "后端异常-放行"}
    except httpx.TimeoutException:
        logger.warning("Token 验证超时: Java 后端不可达, 放行")
        return {"id": -2, "username": "timeout", "nickname": "后端超时-放行"}
    except Exception as e:
        logger.warning(f"Token 验证异常: {e}, 放行")
        return {"id": -3, "username": "error", "nickname": "验证异常-放行"}
