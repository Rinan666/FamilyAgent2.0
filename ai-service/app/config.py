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

    # LLM
    claude_api_key: Optional[str] = None
    deepseek_api_key: Optional[str] = None
    openai_api_key: Optional[str] = None
    default_llm_model: str = "deepseek/deepseek-chat"
    fallback_llm_model: str = "deepseek/deepseek-chat"

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


settings = Settings()
