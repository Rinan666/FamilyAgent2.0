@echo off
set DEEPSEEK_API_KEY=sk-1cd74437a7e9439090b99d4d3758f9c4
set DEFAULT_LLM_MODEL=deepseek/deepseek-chat
set FALLBACK_LLM_MODEL=deepseek/deepseek-chat
uvicorn app.main:app --host 0.0.0.0 --port 8000
