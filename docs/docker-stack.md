# Docker Stack

## Start

```bash
cp .env.docker.example .env.docker
./scripts/server-deploy.sh
```

- Frontend: `3000`
- Backend: `8080`
- AI: `8000`
- Logs: `./scripts/docker-stack.sh logs`
- Stop: `./scripts/docker-stack.sh down`

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
docker compose --env-file .env.docker -f docker-compose.stack.yml config | grep -E 'DB_PASSWORD|REDIS_PASSWORD|RABBITMQ_PASSWORD|AI_INTERNAL_SERVICE_TOKEN|MINIO_ACCESS_KEY|MINIO_SECRET_KEY'
```

## Rebuild After Dependency Changes

The stack builds images from the local repository. When `requirements.txt`, `package-lock.json`, Dockerfiles, or backend dependencies change, recreate the affected service instead of only restarting it:

```bash
docker compose --env-file .env.docker -f docker-compose.stack.yml up -d --build ai-service
docker compose --env-file .env.docker -f docker-compose.stack.yml up -d --build backend
docker compose --env-file .env.docker -f docker-compose.stack.yml up -d --build frontend
```

Use `--no-cache` if pip/npm/maven keeps an old dependency layer:

```bash
docker compose --env-file .env.docker -f docker-compose.stack.yml build --no-cache ai-service
docker compose --env-file .env.docker -f docker-compose.stack.yml up -d ai-service
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
docker compose --env-file .env.docker -f docker-compose.stack.yml build --no-cache ai-service
docker compose --env-file .env.docker -f docker-compose.stack.yml up -d ai-service
```

Verify inside the container:

```bash
docker compose --env-file .env.docker -f docker-compose.stack.yml exec ai-service python -c "import insightface, onnxruntime; print('dip dependencies ok')"
```

Then check logs:

```bash
docker compose --env-file .env.docker -f docker-compose.stack.yml logs --tail=120 ai-service
```

The first face-clustering request may download or initialize InsightFace model files, so the first request can be slower than later requests.

## Troubleshooting Checklist

Check container status:

```bash
docker compose --env-file .env.docker -f docker-compose.stack.yml ps
```

Check service logs:

```bash
docker compose --env-file .env.docker -f docker-compose.stack.yml logs --tail=200 backend
docker compose --env-file .env.docker -f docker-compose.stack.yml logs --tail=200 ai-service
docker compose --env-file .env.docker -f docker-compose.stack.yml logs --tail=200 frontend
```

Check backend health:

```bash
curl http://127.0.0.1:8080/actuator/health
```

Check AI service health:

```bash
curl http://127.0.0.1:8000/ai/health
```

For login issues, first confirm the backend is running and not failing production secret checks. For album face-clustering issues, first confirm the AI container has `insightface` and `onnxruntime` installed.
