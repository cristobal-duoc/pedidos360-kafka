#!/usr/bin/env bash
# Publica los dos microservicios de mensajeria (notificaciones y rabbit-admin):
# construye cada imagen (linux/amd64), la sube a su ECR y fuerza a Fargate a tomarla.
# Ejecutar desde la raiz del repo, con el lab arrancado y despues de terraform apply.
set -euo pipefail

ESTUDIANTE="${ESTUDIANTE:-martinez}"
REGION="${AWS_REGION:-us-east-1}"
CLUSTER="dsy1107-backend-${ESTUDIANTE}"
CUENTA=$(aws sts get-caller-identity --query Account --output text)
REGISTRO="${CUENTA}.dkr.ecr.${REGION}.amazonaws.com"

echo "==> Autenticando contra ECR"
aws ecr get-login-password --region "${REGION}" \
  | docker login --username AWS --password-stdin "${REGISTRO}"

for MS in notificaciones rabbit-admin; do
  IMAGEN="${REGISTRO}/dsy1107-${MS}-${ESTUDIANTE}:latest"
  echo "==> ${MS}: construyendo y subiendo ${IMAGEN}"
  docker build --platform linux/amd64 -t "${IMAGEN}" "${MS}"
  docker push "${IMAGEN}"
  aws ecs update-service --cluster "${CLUSTER}" --service "${MS}" \
    --task-definition "dsy1107-${MS}-${ESTUDIANTE}" --force-new-deployment >/dev/null
done

echo "==> Esperando que los dos servicios queden estables"
aws ecs wait services-stable --cluster "${CLUSTER}" --services notificaciones rabbit-admin

TASK=$(aws ecs list-tasks --cluster "${CLUSTER}" --service-name rabbit-admin --query 'taskArns[0]' --output text)
ENI=$(aws ecs describe-tasks --cluster "${CLUSTER}" --tasks "${TASK}" \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)
IP=$(aws ec2 describe-network-interfaces --network-interface-ids "${ENI}" \
  --query 'NetworkInterfaces[0].Association.PublicIp' --output text)

echo
echo "LISTO."
echo "  API del administrador : http://${IP}:8097/api/rabbitmq"
echo "  Paneles de RabbitMQ   : $(terraform -chdir=terraform output -json rabbitmq_paneles)"
echo "  Logs de los consumidores (CloudWatch): /ecs/dsy1107-notificaciones-${ESTUDIANTE}"
