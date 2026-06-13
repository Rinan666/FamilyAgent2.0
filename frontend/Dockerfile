FROM node:22-alpine AS build

WORKDIR /app

ARG BACKEND_URL=http://backend:8080
ARG AI_SERVICE_URL=http://ai-service:8000

ENV BACKEND_URL=$BACKEND_URL
ENV AI_SERVICE_URL=$AI_SERVICE_URL

COPY package.json package-lock.json ./
RUN npm ci --include=dev --registry=https://registry.npmmirror.com

COPY . .
RUN npm run build --registry=https://registry.npmmirror.com && npm prune --omit=dev --registry=https://registry.npmmirror.com

FROM node:22-alpine AS runtime

WORKDIR /app

ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

COPY --from=build /app/package.json ./package.json
COPY --from=build /app/package-lock.json ./package-lock.json
COPY --from=build /app/next.config.js ./next.config.js
COPY --from=build /app/.next ./.next
COPY --from=build /app/node_modules ./node_modules

RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

USER appuser

EXPOSE 3000

CMD ["npm", "run", "start", "--", "--hostname", "0.0.0.0", "--port", "3000"]
