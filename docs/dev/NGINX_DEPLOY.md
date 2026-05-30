# Minimal Nginx Deployment

This deployment serves the Vite production build from Nginx and proxies backend API calls to the Spring Boot app:

```text
browser -> nginx:80 -> /api/* -> app:9000
browser -> nginx:80 -> static frontend files
```

Compose service:

```powershell
docker compose --env-file .env.example up -d --build nginx
```

The Nginx image is built from `docker/nginx/Dockerfile`. The final runtime stage uses the official `nginx:1.31.1-alpine` image and avoids older 1.20/1.22-era tags.

## Chat SSE Proxying

The frontend uses relative `/api` paths. Chat streaming endpoints are:

```text
POST /api/chat-sessions/stream
POST /api/chat-sessions/{sessionId}/messages/stream
```

For these routes, `docker/nginx/default.conf` disables proxy buffering and cache, keeps HTTP/1.1 upstream connections, disables gzip, and sets long read/send timeouts so `meta`, `delta`, `done`, and `error` events can pass through without Nginx coalescing the response.
