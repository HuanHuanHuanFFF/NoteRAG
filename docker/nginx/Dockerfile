FROM node:22-alpine AS frontend-build

WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM nginx:1.31.1-alpine

COPY docker/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /workspace/frontend/dist/ /usr/share/nginx/html/

EXPOSE 80
