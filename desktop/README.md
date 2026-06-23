# FamilyAgent Desktop Launcher

This directory packages FamilyAgent as a Windows desktop launcher. The launcher does not embed every runtime into one binary. Instead, it starts the existing Docker Compose stack and opens the local web app in an Electron window.

## Prerequisites

- Windows 10/11
- Docker Desktop with Docker Compose
- Network access for the first image build and dependency download
- Configured AI provider keys and internal tokens

## Development

```powershell
cd D:\FamilyAgent\desktop
npm install
npm run dev
```

In development mode, the launcher uses the repository root as the stack directory.

## Build Installer

```powershell
cd D:\FamilyAgent\desktop
npm install
npm run dist:win
```

The build command first creates a trimmed Docker stack under `desktop\.stack`, then writes the installer to `desktop\dist`.

Common outputs:

- `desktop\dist\FamilyAgent Setup 0.1.0.exe`
- `desktop\dist\win-unpacked\FamilyAgent.exe`

`desktop\.stack` and `desktop\dist` are generated artifacts and are ignored by git.

## Runtime Behavior

- On first launch, the app copies `.env.docker.example` into the Electron user data directory as `.env.docker`.
- The app menu has an item for opening that config file.
- The launcher sets `STACK_ENV_FILE` to the user-data config file and runs:

```powershell
docker compose --env-file <user-data>\config\.env.docker -f <stack>\docker-compose.stack.yml up -d --build --remove-orphans
```

- Closing the desktop window does not stop containers. Use the stop-services menu item when you want to run `docker compose down`.

## Packaged Stack

The installer includes:

- `backend`
- `frontend`
- `ai-service`
- `docker-compose.stack.yml`
- `.env.docker.example`

Generated folders such as `node_modules`, `.next`, `target`, virtual environments, logs, and caches are excluded.
