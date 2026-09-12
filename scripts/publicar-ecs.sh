#!/usr/bin/env bash
# Publica el backend: compila el .jar, construye la imagen (linux/amd64), la sube
# a ECR, fuerza a Fargate a tomarla, y reapunta las integraciones del API Gateway
# a la IP nueva de la task. Ejecutar desde la raíz del repo, con el lab arrancado.
set -euo pipefail

ESTUDIANTE="${ESTUDIANTE:-martinez}"   # cámbialo o expórtalo
REGION="${AWS_REGION:-us-east-1}"

echo "==> Leyendo datos de Terraform"
ECR=$(terraform -chdir=terraform output -raw ecr_url)
CLUSTER="dsy1107-backend-${ESTUDIANTE}"

echo "==> 1/5 Construyendo la imagen (linux/amd64) — el Dockerfile compila el .jar"
docker build --platform linux/amd64 -t "${ECR}:latest" backend

echo "==> 2/5 Autenticando contra ECR"
aws ecr get-login-password --region "${REGION}" \
  | docker login --username AWS --password-stdin "${ECR%%/*}"

echo "==> 3/5 Subiendo la imagen"
docker push "${ECR}:latest"

echo "==> 4/5 Redesplegando el servicio ECS"
aws ecs update-service --cluster "${CLUSTER}" --service backend \
  --force-new-deployment >/dev/null

echo "==> 5/5 Esperando la IP pública de la task nueva"
aws ecs wait services-stable --cluster "${CLUSTER}" --services backend || true
TASK=$(aws ecs list-tasks --cluster "${CLUSTER}" --service-name backend --query 'taskArns[0]' --output text)
ENI=$(aws ecs describe-tasks --cluster "${CLUSTER}" --tasks "${TASK}" \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)
IP=$(aws ec2 describe-network-interfaces --network-interface-ids "${ENI}" \
  --query 'NetworkInterfaces[0].Association.PublicIp' --output text)

echo "==> Reapuntando el API Gateway a http://${IP}:8080"
API=$(aws apigatewayv2 get-apis --query "Items[?Name=='pedidos360-${ESTUDIANTE}'].ApiId" --output text)
# Hay DOS integraciones (/productos y /productos/{id}): se reapunta cada una
# conservando su propio path, porque solo cambia el host.
for INT in $(aws apigatewayv2 get-integrations --api-id "${API}" --query 'Items[].IntegrationId' --output text); do
  URI=$(aws apigatewayv2 get-integration --api-id "${API}" --integration-id "${INT}" --query IntegrationUri --output text)
  RUTA="/${URI#*://*/}"
  aws apigatewayv2 update-integration --api-id "${API}" --integration-id "${INT}" --integration-uri "http://${IP}:8080${RUTA}" >/dev/null
  echo "    ${INT} -> http://${IP}:8080${RUTA}"
done

echo "==> Listo. Backend en http://${IP}:8080 ; API en $(terraform -chdir=terraform output -raw api_url)"
