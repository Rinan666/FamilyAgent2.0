# Docker Stack

## Start

Docker Compose is the standard startup path for the complete application stack.

The base file keeps all services on the internal Compose network. Local development adds loopback-only host ports:

```powershell
docker compose --env-file .env `
  -f compose.yml `
  -f deploy/compose/local.yml `
  up -d --build
```

Production adds Cloudflare Tunnel without publishing database, queue, storage, AI, or Backend ports:

```bash
cp deploy/examples/stack.env.example .env.docker
docker compose --env-file .env.docker \
  -f compose.yml \
  -f deploy/compose/production.yml \
  up -d --build
```

The helper script selects the same overrides:

```bash
./scripts/docker-stack.sh up
STACK_MODE=production ./scripts/docker-stack.sh up
```

Keep `COMPOSE_PROJECT_NAME` aligned with the existing Compose project on each host. Existing cloud deployments using `fa-*` containers should set:

```env
COMPOSE_PROJECT_NAME=fa
```

Changing the project name on an existing host makes Compose treat the stack as a different application and can create duplicate containers or port conflicts. The local `.env.example` uses `familyagent`, while `deploy/examples/stack.env.example` uses `fa` for server compatibility.

- Local Frontend: `127.0.0.1:3000`
- Local Backend: `127.0.0.1:8080`
- Local AI: `127.0.0.1:8000`
- Public tunnel: Cloudflare routes to the `frontend` container
- Production host ports: none

The host PowerShell launcher is not required for the Docker Stack.

## Cloudflare Tunnel

The stack runs `cloudflared` as a container. Configure the named tunnel ID and the absolute host path to its credential JSON:

```env
CLOUDFLARED_TUNNEL_ID=00000000-0000-0000-0000-000000000000
CLOUDFLARED_CREDENTIALS_FILE=C:/Users/your-name/.cloudflared/00000000-0000-0000-0000-000000000000.json
```

On Linux, use an absolute Linux path for `CLOUDFLARED_CREDENTIALS_FILE`. The credential file is mounted read-only and is never copied into the image.

## Production HTTP Exposure

- Swagger UI and OpenAPI JSON are disabled by the `prod` Spring profile.
- `/actuator/health` remains public for health checks.
- Other actuator endpoints, including `/actuator/metrics`, require a valid Sa-Token login.
- Only Cloudflare Tunnel should receive public traffic in the production override.

## Invite Codes

Migration V23 archives the public `FAMILY001` through `FAMILY010` seed codes. The development profile creates only `DEV-FAMILY-LOCAL` through a separate Flyway location.

Generate a production invite with a random value, expiry, and usage limit:

```bash
bash scripts/generate-invite-code.sh 1 30 operations "Family onboarding"
```

The first argument is maximum uses and the second is validity in days. Deliver the printed code through a private channel.

## Backup and Restore

See [Backup and Restore](backup-restore.md) for encrypted PostgreSQL and MinIO backups, retention, offsite copies, the systemd timer, and restore drills.

## Required Production Secrets

The production backend refuses to start when required secrets are empty or still use local/demo defaults. Before deploying, set strong non-default values in `.env.docker`:

```env
DB_PASSWORD=change-to-a-strong-password
REDIS_PASSWORD=change-to-a-strong-password
RABBITMQ_PASSWORD=change-to-a-strong-password
AI_INTERNAL_SERVICE_TOKEN=change-to-a-long-random-token
MINIO_ACCESS_KEY=change-to-a-non-default-access-key
MINIO_SECRET_KEY=change-to-a-strong-secret-key
```

Do not keep these default values in production:

```env
DB_PASSWORD=fa_dev_pass
REDIS_PASSWORD=ASDFGZXCVB008
RABBITMQ_PASSWORD=fa_dev_pass
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
AI_INTERNAL_SERVICE_TOKEN=familyagent-dev-internal-token
```

Generate random values on the server when needed:

```bash
openssl rand -hex 24
```

Check the values that Docker Compose will inject:

```bash
STACK_MODE=production ./scripts/docker-stack.sh config | grep -E 'DB_PASSWORD|REDIS_PASSWORD|RABBITMQ_PASSWORD|AI_INTERNAL_SERVICE_TOKEN|MINIO_ACCESS_KEY|MINIO_SECRET_KEY'
```

## Image Mirror Overrides

If the server cannot pull Docker Hub images and reports errors such as `403 Forbidden` from a registry mirror, override the images in `.env.docker` instead of editing Dockerfiles:

```env
DOCKER_POSTGRES_IMAGE=docker.m.daocloud.io/pgvector/pgvector:pg16
DOCKER_REDIS_IMAGE=docker.m.daocloud.io/library/redis:7-alpine
DOCKER_RABBITMQ_IMAGE=docker.m.daocloud.io/library/rabbitmq:3.13-management-alpine
DOCKER_MINIO_IMAGE=docker.m.daocloud.io/minio/minio:latest
DOCKER_MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9.9-eclipse-temurin-17
DOCKER_JAVA_RUNTIME_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:17-jre-jammy
DOCKER_PYTHON_IMAGE=docker.m.daocloud.io/library/python:3.12-slim
DOCKER_NODE_IMAGE=docker.m.daocloud.io/library/node:22-alpine
```

## Rebuild After Dependency Changes

The stack builds images from the local repository. When `requirements.txt`, `package-lock.json`, Dockerfiles, or backend dependencies change, recreate the affected service instead of only restarting it:

```bash
docker compose --env-file .env.docker -f compose.yml up -d --build ai-service
docker compose --env-file .env.docker -f compose.yml up -d --build backend
docker compose --env-file .env.docker -f compose.yml up -d --build frontend
```

Use `--no-cache` if pip/npm/maven keeps an old dependency layer:

```bash
docker compose --env-file .env.docker -f compose.yml build --no-cache ai-service
docker compose --env-file .env.docker -f compose.yml up -d ai-service
```

## Album Face Clustering Dependencies

Album face clustering is served by the AI service under `/ai/dip/faces/*`. It imports `dip.router`, which requires the DIP Python dependencies from `ai-service/requirements.txt`, including:

```txt
opencv-python-headless
insightface
onnxruntime
scikit-learn
matplotlib
scikit-image
```

If the UI or logs show this message:

```text
DIP image processing is unavailable because optional dependency 'insightface' is not installed.
Install ai-service requirements to enable these endpoints.
```

it means the running `ai-service` image does not contain `insightface`. Rebuild the AI image and recreate the container:

```bash
docker compose --env-file .env.docker -f compose.yml build --no-cache ai-service
docker compose --env-file .env.docker -f compose.yml up -d ai-service
```

Verify inside the container:

```bash
docker compose --env-file .env.docker -f compose.yml exec ai-service python -c "import insightface, onnxruntime; print('dip dependencies ok')"
```

Then check logs:

```bash
docker compose --env-file .env.docker -f compose.yml logs --tail=120 ai-service
```

The first face-clustering request may download or initialize InsightFace model files, so the first request can be slower than later requests.

## Troubleshooting Checklist

Check container status:

```bash
STACK_MODE=production ./scripts/docker-stack.sh status
```

Check service logs:

```bash
STACK_MODE=production ./scripts/docker-stack.sh logs backend
STACK_MODE=production ./scripts/docker-stack.sh logs ai-service
STACK_MODE=production ./scripts/docker-stack.sh logs frontend
STACK_MODE=production ./scripts/docker-stack.sh logs cloudflared
```

Production services are not published on host ports. Check their container health through Compose:

```bash
STACK_MODE=production ./scripts/docker-stack.sh status
```

For login issues, first confirm the backend is running and not failing production secret checks. For album face-clustering issues, first confirm the AI container has `insightface` and `onnxruntime` installed.
