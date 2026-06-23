#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────
# OpenAtom Knowledge OS — 服务器端首次部署脚本
# 在服务器上执行：bash deploy.sh
# ─────────────────────────────────────────────

DEPLOY_DIR="${HOME}/openatom-deploy"
COMPOSE_FILE="${DEPLOY_DIR}/docker-compose.prod.yml"
ENV_FILE="${DEPLOY_DIR}/.env"

echo "=== OpenAtom Knowledge OS 部署 ==="

# 1. 创建部署目录
mkdir -p "$DEPLOY_DIR"

# 2. 检查 .env 文件，首次部署自动从 .env.example 创建
if [ ! -f "$ENV_FILE" ]; then
  if [ -f "${DEPLOY_DIR}/.env.example" ]; then
    echo "→ 首次部署，从 .env.example 创建 .env"
    cp "${DEPLOY_DIR}/.env.example" "$ENV_FILE"
    echo "⚠ 请稍后编辑 ${ENV_FILE} 修改密码等敏感配置"
  else
    echo "⚠ 未找到 ${ENV_FILE} 且无 .env.example 模板，请手动创建。"
    exit 1
  fi
fi

# 3. 加载 Docker 镜像（CI 会通过 SCP 上传 backend-image.tar）
if [ -f "${DEPLOY_DIR}/backend-image.tar" ]; then
  echo "→ 加载后端 Docker 镜像..."
  docker load -i "${DEPLOY_DIR}/backend-image.tar"
  docker tag openatom-knowledge-backend:latest openatom-knowledge-backend:latest 2>/dev/null || true
fi

# 4. 拉取基础设施镜像并启动
echo "→ 启动服务..."
cd "$DEPLOY_DIR"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

# 5. 等待后端健康检查
echo "→ 等待后端启动..."
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1:10001/actuator/health > /dev/null 2>&1; then
    echo "✓ 后端已就绪: http://127.0.0.1:10001"
    exit 0
  fi
  sleep 3
done

echo "✗ 后端启动超时，请检查日志: docker compose -f $COMPOSE_FILE logs backend"
exit 1
