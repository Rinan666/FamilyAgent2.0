# Bug 修复记录

---

## 2026-06-14 日记页面"帮我整理"AI 服务不可用

### 现象

- 前端日记页面点击"帮我整理"按钮，报错 `Failed`
- Next.js 控制台：`Failed to proxy http://localhost:8000/ai/memory/organize-draft Error: socket hang up (ECONNRESET)`
- AI 服务无任何请求日志

### 根因（三层叠加）

**层一：httpx `proxy=None` 是非法参数（ai-service）**

`auth.py` 为了绕开 Windows 系统代理，给 `httpx.AsyncClient` 传了 `proxy=None`，但 httpx 0.28 不接受该参数，会在 `_call_backend_verify` 里抛 `TypeError`。异常被 `except Exception` 捕获后走 fail-open 放行，但有时会使 uvicorn async context 状态异常。

**层二：httpx 仍然读取 Windows 系统代理（ai-service）**

即使不抛异常，`proxy=None` 也无法禁用系统代理。httpx 默认读取 `HTTP_PROXY`/`HTTPS_PROXY` 环境变量，导致 `_call_backend_verify` 调用 `localhost:8080/api/users/me` 时走了系统代理，返回 502。日志明确记录：`Token verification backend unavailable: status=502, AUTH_FAIL_OPEN=true`。

**层三：Next.js rewrite 代理复用死连接（frontend）**

AI 服务重启后，Next.js 生产模式的 rewrite 代理保留了旧 keep-alive 连接池。复用已关闭的连接时，Node.js 收到 ECONNRESET，并在 TCP 层直接断开，请求完全没有到达 FastAPI handler（所以 AI 服务无日志）。

### 修复

**1. `ai-service/app/middleware/auth.py`**

```python
# 修复前
_NO_PROXY_CLIENT_ARGS = {"proxy": None, "timeout": 5.0}

# 修复后
_NO_PROXY_CLIENT_ARGS = {"trust_env": False, "timeout": 5.0}
```

`trust_env=False` 是 httpx 合法参数，告诉 httpx 完全忽略环境变量里的代理配置，直连 localhost。

**2. `frontend/src/app/ai-proxy/[...path]/route.ts`（新建）**

用 Next.js App Router route handler 替代 `next.config.js` 的 rewrite 规则，在应用层自己发起 fetch 请求，设置 `Connection: close` 头，每次请求建立新连接，不复用连接池。

**3. `frontend/next.config.js`**

删除 `/ai-proxy/:path*` rewrite 规则，交由新 route handler 处理。

### 经验

- httpx 禁用代理用 `trust_env=False`，不要用 `proxy=None`
- Next.js `httpAgentOptions: { keepAlive: false }` 只影响 SSR fetch，**不影响** rewrite 代理的连接池
- rewrite 代理无法控制 keep-alive 行为，长耗时的 AI 请求最好用 route handler 自定义代理
- fail-open 会掩盖认证层异常，生产环境排查时注意区分"认证失败"和"认证服务不可用"

---
