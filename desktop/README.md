# FamilyAgent Desktop Launcher

This directory packages FamilyAgent as a Windows desktop launcher. The launcher does not embed every runtime into one binary. Instead, it starts the existing Docker Compose stack and opens the local web app in an Electron window.

## Prerequisites

- Windows 10/11
- Docker Desktop with Docker Compose
- Network access for the first image build and dependency download
- A configured `.env.docker` file, especially AI provider keys and internal tokens

## Development

```powershell
cd D:\FamilyAgent\desktop
npm install
npm run dev
```

In development mode the launcher uses the repository root as the stack directory.

## Build Installer

```powershell
cd D:\FamilyAgent\desktop
npm install
npm run dist:win
```

The installer is written to `desktop\dist`.

## Runtime Behavior

- On first launch, the app copies `.env.docker.example` into the Electron user data directory as `.env.docker`.
- The menu item `打开配置文件` opens that file for editing.
- The launcher runs:

```powershell
docker compose --env-file <user-data>\config\.env.docker -f <stack>\docker-compose.stack.yml up -d --build --remove-orphans
```

- Closing the desktop window does not stop containers. Use `停止服务` from the app menu when you want to run `docker compose down`.

## Packaged Stack

The installer includes:

- `backend`
- `frontend`
- `ai-service`
- `docker-compose.stack.yml`
- `.env.docker.example`

Generated folders such as `node_modules`, `.next`, `target`, virtual environments, logs, and caches are excluded.
