#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

AWS_PROFILE="${AWS_PROFILE:-eks-role}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
CLUSTER_NAME="${CLUSTER_NAME:-wearhouse-eks}"
NAMESPACE="${NAMESPACE:-wearhouse}"
IMAGE_TAG="${IMAGE_TAG:-$(date +%Y%m%d%H%M%S)}"
DOCKER_PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"

MODULES_DEFAULT=(
  service-discovery
  config-server
  auth
  user
  product
  cart
  order
  payment
  inventory
  api-gateway
)
MODULES=("${MODULES_DEFAULT[@]}")
if [ -n "${MODULES_OVERRIDE:-}" ]; then
  MODULES_OVERRIDE="${MODULES_OVERRIDE//,/ }"
  read -r -a MODULES <<<"$MODULES_OVERRIDE"
fi

ROLLOUT_TARGETS_DEFAULT=(
  mysql
  redis-auth
  redis-order
  zookeeper
  kafka
  service-discovery
  config-server
  auth-service
  user-service
  product-service
  cart-service
  order-service
  payment-service
  inventory-service
  api-gateway
)
ROLLOUT_TARGETS=("${ROLLOUT_TARGETS_DEFAULT[@]}")
if [ -n "${ROLLOUT_TARGETS_OVERRIDE:-}" ]; then
  ROLLOUT_TARGETS_OVERRIDE="${ROLLOUT_TARGETS_OVERRIDE//,/ }"
  read -r -a ROLLOUT_TARGETS <<<"$ROLLOUT_TARGETS_OVERRIDE"
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] required command not found: $1" >&2
    exit 1
  fi
}

require_cmd aws
require_cmd kubectl
require_cmd docker
require_cmd eksctl

# Load defaults first, then local overrides.
set -a
if [ -f .env.example ]; then
  . ./.env.example
fi
if [ -f .env ]; then
  . ./.env
fi
set +a

# Required secrets/defaults.
: "${MYSQL_ROOT_PASSWORD:=wearhouse_root_password}"
: "${MYSQL_USER:=wearhouse}"
: "${MYSQL_PASSWORD:=wearhouse_password}"
: "${AUTH_JWT_SECRET:=wearhouse-local-jwt-secret-key-change-me-please-1234567890}"
: "${AUTH_INTERNAL_SHARED_SECRET:=wearhouse-internal-shared-secret}"
: "${PASSPORT_SHARED_SECRET:=wearhouse-passport-shared-secret}"
: "${USER_AUTH_INTERNAL_SHARED_SECRET:=$AUTH_INTERNAL_SHARED_SECRET}"
: "${ORDER_INTERNAL_SHARED_SECRET:=wearhouse-order-internal-secret}"
: "${WALLET_SECRET_KEY:=pay_secret_key}"
: "${SPRING_MAIL_USERNAME:=}"
: "${SPRING_MAIL_PASSWORD:=}"
: "${S3_ACCESS_KEY:=}"
: "${S3_SECRET_KEY:=}"

export AWS_PROFILE AWS_REGION

if ! aws eks describe-cluster --name "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null 2>&1; then
  echo "[INFO] creating EKS cluster: $CLUSTER_NAME"
  eksctl create cluster \
    --name "$CLUSTER_NAME" \
    --region "$AWS_REGION" \
    --managed \
    --node-type t3.large \
    --nodes 1 \
    --nodes-min 1 \
    --nodes-max 2 \
    --with-oidc
fi

echo "[INFO] updating kubeconfig"
aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null

echo "[INFO] waiting for nodes"
kubectl wait --for=condition=Ready node --all --timeout=20m

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

echo "[INFO] logging into ECR: $ECR_REGISTRY"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY" >/dev/null

for module in "${MODULES[@]}"; do
  repo_name="wearhouse/${module}"
  if ! aws ecr describe-repositories --repository-names "$repo_name" --region "$AWS_REGION" >/dev/null 2>&1; then
    echo "[INFO] creating ECR repository: $repo_name"
    aws ecr create-repository --repository-name "$repo_name" --region "$AWS_REGION" >/dev/null
  fi

done

for module in "${MODULES[@]}"; do
  image="${ECR_REGISTRY}/wearhouse/${module}:${IMAGE_TAG}"
  echo "[INFO] building ${module} -> ${image}"
  docker build \
    -f docker/java-service.Dockerfile \
    --platform "$DOCKER_PLATFORM" \
    --build-arg MODULE_NAME="$module" \
    -t "$image" \
    .

  echo "[INFO] pushing ${image}"
  docker push "$image" >/dev/null

done

echo "[INFO] ensuring namespace: ${NAMESPACE}"
kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE" >/dev/null

echo "[INFO] applying configmap: wearhouse-config-repo"
kubectl -n "$NAMESPACE" create configmap wearhouse-config-repo \
  --from-file=config-repo \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

echo "[INFO] applying configmap: wearhouse-mysql-init"
kubectl -n "$NAMESPACE" create configmap wearhouse-mysql-init \
  --from-file=01-init-databases.sh=docker/mysql/init/01-init-databases.sh \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

echo "[INFO] applying secret: wearhouse-secrets"
kubectl -n "$NAMESPACE" create secret generic wearhouse-secrets \
  --from-literal=MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
  --from-literal=MYSQL_USER="$MYSQL_USER" \
  --from-literal=MYSQL_PASSWORD="$MYSQL_PASSWORD" \
  --from-literal=AUTH_JWT_SECRET="$AUTH_JWT_SECRET" \
  --from-literal=AUTH_INTERNAL_SHARED_SECRET="$AUTH_INTERNAL_SHARED_SECRET" \
  --from-literal=PASSPORT_SHARED_SECRET="$PASSPORT_SHARED_SECRET" \
  --from-literal=USER_AUTH_INTERNAL_SHARED_SECRET="$USER_AUTH_INTERNAL_SHARED_SECRET" \
  --from-literal=ORDER_INTERNAL_SHARED_SECRET="$ORDER_INTERNAL_SHARED_SECRET" \
  --from-literal=WALLET_SECRET_KEY="$WALLET_SECRET_KEY" \
  --from-literal=SPRING_MAIL_USERNAME="$SPRING_MAIL_USERNAME" \
  --from-literal=SPRING_MAIL_PASSWORD="$SPRING_MAIL_PASSWORD" \
  --from-literal=S3_ACCESS_KEY="$S3_ACCESS_KEY" \
  --from-literal=S3_SECRET_KEY="$S3_SECRET_KEY" \
  --dry-run=client -o yaml | kubectl apply -f - >/dev/null

echo "[INFO] applying kubernetes manifests"
sed \
  -e "s|__ECR_REGISTRY__|${ECR_REGISTRY}|g" \
  -e "s|__IMAGE_TAG__|${IMAGE_TAG}|g" \
  k8s/wearhouse-template.yaml | kubectl apply -f - >/dev/null

echo "[INFO] waiting for core deployments"
for deploy in "${ROLLOUT_TARGETS[@]}"; do
  kubectl -n "$NAMESPACE" rollout status "deployment/${deploy}" --timeout=20m

done

echo "[INFO] service summary"
kubectl -n "$NAMESPACE" get svc

echo "[INFO] gateway external endpoint"
kubectl -n "$NAMESPACE" get svc api-gateway -o jsonpath='{.status.loadBalancer.ingress[0].hostname}{"\n"}' || true
kubectl -n "$NAMESPACE" get svc api-gateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}{"\n"}' || true

echo "[DONE] deployment complete"
echo "[DONE] image tag: ${IMAGE_TAG}"
