@echo off
if "%DASHSCOPE_API_KEY%"=="" (
  echo DASHSCOPE_API_KEY is not set. Please configure it in ai-service/.env or your shell.
)
set DEFAULT_LLM_MODEL=dashscope/qwen-flash
set FALLBACK_LLM_MODEL=dashscope/qwen-turbo
set EMBEDDING_MODEL=dashscope-multimodal/qwen3-vl-embedding
set EMBEDDING_DIMENSION=1536
uvicorn app.main:app --host 0.0.0.0 --port 8000
