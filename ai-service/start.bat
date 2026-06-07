@echo off
if "%DEEPSEEK_API_KEY%"=="" (
  echo DEEPSEEK_API_KEY is not set. Please configure it in ai-service/.env or your shell.
)
set DEFAULT_LLM_MODEL=deepseek/deepseek-chat
set FALLBACK_LLM_MODEL=deepseek/deepseek-chat
uvicorn app.main:app --host 0.0.0.0 --port 8000
