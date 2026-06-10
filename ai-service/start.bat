@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "ROOT_ENV=%SCRIPT_DIR%..\.env"
set "VENV_PYTHON=%SCRIPT_DIR%.venv\Scripts\python.exe"

if exist "%ROOT_ENV%" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT_ENV%") do (
    if not "%%~A"=="" set "%%~A=%%~B"
  )
)

if not exist "%VENV_PYTHON%" (
  echo [ERROR] Project virtual environment not found: "%VENV_PYTHON%"
  echo Create it with:
  echo   py -3.12 -m venv .venv
  echo   .\.venv\Scripts\python.exe -m pip install -r requirements.txt
  exit /b 1
)

if "%AI_SERVICE_PORT%"=="" set AI_SERVICE_PORT=8090
"%VENV_PYTHON%" -m ruff --version >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Ruff is not installed in the project virtual environment.
  echo Run:
  echo   .\.venv\Scripts\python.exe -m pip install -r requirements.txt
  exit /b 1
)
"%VENV_PYTHON%" -m uvicorn app.main:app --host 0.0.0.0 --port %AI_SERVICE_PORT%
