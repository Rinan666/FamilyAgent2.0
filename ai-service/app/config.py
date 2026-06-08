"""
应用配置管理
"""
import os
from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """应用配置，从环境变量加载"""

    # 应用
    app_env: str = "development"
    app_debug: bool = True

    # AI 服务
    ai_service_port: int = 8000

    # Java 后端（Token 验证用）
    backend_url: str = "http://localhost:8080"
    auth_fail_open: Optional[bool] = None
    cors_allow_origins: Optional[str] = None

    # LLM
    claude_api_key: Optional[str] = None
    deepseek_api_key: Optional[str] = None
    openai_api_key: Optional[str] = None
    dashscope_api_key: Optional[str] = None
    dashscope_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    dashscope_multimodal_url: str = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding"
    default_llm_model: str = "dashscope/qwen-plus"
    fallback_llm_model: str = "dashscope/qwen-plus"
    embedding_model: str = "dashscope-multimodal/tongyi-embedding-vision-flash-2026-03-06"
    embedding_dimension: int = 1536

    # Web search for time-sensitive public facts.
    web_search_enabled: bool = True
    web_search_provider: str = "duckduckgo"
    web_search_timeout_seconds: float = 8.0
    web_search_max_results: int = 4
    tavily_api_key: Optional[str] = None

    # AI cost and DoS protection.
    ai_max_message_chars: int = 8000
    ai_max_total_input_chars: int = 20000
    ai_max_output_tokens: int = 1800
    ai_hard_timeout_seconds: float = 100.0
    ai_stream_idle_timeout_seconds: float = 20.0
    ai_global_concurrency: int = 8
    ai_user_concurrency: int = 2
    ai_user_rate_limit_per_minute: int = 20
    ai_ip_rate_limit_per_minute: int = 60
    ai_embedding_user_rate_limit_per_minute: int = 120
    ai_embedding_ip_rate_limit_per_minute: int = 240
    ai_embedding_timeout_seconds: float = 30.0

    # 数据库
    db_host: str = "localhost"
    db_port: int = 5432
    db_name: str = "familyagent"
    db_user: str = "fa_user"
    db_password: str = "fa_dev_pass"

    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: Optional[str] = None

    # RabbitMQ
    rabbitmq_host: str = "localhost"
    rabbitmq_port: int = 5672
    rabbitmq_user: str = "fa_user"
    rabbitmq_password: str = "fa_dev_pass"

    # 数学执行沙箱
    math_sandbox_timeout: int = 5
    math_sandbox_max_memory_mb: int = 256

    # 日志
    log_level: str = "DEBUG"

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "extra": "ignore",
    }

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        # LiteLLM needs API keys in OS environment
        if self.deepseek_api_key and not os.environ.get("DEEPSEEK_API_KEY"):
            os.environ["DEEPSEEK_API_KEY"] = self.deepseek_api_key
        if self.claude_api_key and not os.environ.get("ANTHROPIC_API_KEY"):
            os.environ["ANTHROPIC_API_KEY"] = self.claude_api_key
        if self.openai_api_key and not os.environ.get("OPENAI_API_KEY"):
            os.environ["OPENAI_API_KEY"] = self.openai_api_key
        if self.dashscope_api_key and not os.environ.get("DASHSCOPE_API_KEY"):
            os.environ["DASHSCOPE_API_KEY"] = self.dashscope_api_key

    @property
    def auth_fail_open_enabled(self) -> bool:
        """Only development defaults to fail-open; all other envs fail-closed."""
        if self.auth_fail_open is not None:
            return self.auth_fail_open
        return self.app_env.lower() == "development"

    @property
    def cors_origins(self) -> list[str]:
        """Allowed browser origins for the AI service."""
        raw = self.cors_allow_origins
        if not raw:
            if self.app_env.lower() == "development":
                return ["http://localhost:3000", "http://127.0.0.1:3000"]
            return []
        return [origin.strip() for origin in raw.split(",") if origin.strip()]


settings = Settings()
