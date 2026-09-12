#!/usr/bin/env bash
# Revive el sistema tras reiniciar el Learner Lab, SIN reconstruir la imagen
# (ya esta en ECR). Usalo el dia de la presentacion; publicar-ecs.sh solo hace
# falta cuando cambiaste el codigo del backend.
#
#   1. Start Lab en AWS Academy
#   2. AWS Details -> AWS CLI -> Show -> pegar en ~/.aws/credentials
#   3. bash scripts/revivir.sh      (desde la raiz del repo)
set -euo pipefail

ESTUDIANTE="${ESTUDIANTE:-martinez}"
REGION="${AWS_REGION:-us-east-1}"
CLUSTER="dsy1107-backend-${ESTUDIANTE}"

echo "==> 0/5 Comprobando las credenciales del lab"
if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "ERROR: credenciales vencidas o ausentes."
  echo "Start Lab -> AWS Details -> AWS CLI -> Show -> pegar en ~/.aws/credentials"
  exit 1
fi

echo "==> 1/5 Reponiendo lo que el lab se llevo (terraform)"
terraform -chdir=terraform apply -auto-approve

echo "==> 2/5 Asegurando que el servicio ECS tenga una task"
aws ecs update-service --cluster "${CLUSTER}" --service backend \
  --desired-count 1 >/dev/null
aws ecs wait services-stable --cluster "${CLUSTER}" --services backend || true

echo "==> 3/5 Resolviendo la IP publica de la task"
TASK=$(aws ecs list-tasks --cluster "${CLUSTER}" --service-name backend \
  --query 'taskArns[0]' --output text)
if [ "${TASK}" = "None" ] || [ -z "${TASK}" ]; then
  echo "ERROR: no hay ninguna task corriendo. Revisa los logs en CloudWatch:"
  echo "  /ecs/dsy1107-backend-${ESTUDIANTE}"
  exit 1
fi
ENI=$(aws ecs describe-tasks --cluster "${CLUSTER}" --tasks "${TASK}" \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' \
  --output text)
IP=$(aws ec2 describe-network-interfaces --network-interface-ids "${ENI}" \
  --query 'NetworkInterfaces[0].Association.PublicIp' --output text)
echo "    IP de la task: ${IP}"

echo "==> 4/5 Reapuntando el API Gateway"
API=$(aws apigatewayv2 get-apis \
  --query "Items[?Name=='pedidos360-${ESTUDIANTE}'].ApiId" --output text)
# Hay DOS integraciones (/productos y /productos/{id}): se reapunta cada una
# conservando su propio path, porque solo cambia el host.
for INT in $(aws apigatewayv2 get-integrations --api-id "${API}" --query 'Items[].IntegrationId' --output text); do
  URI=$(aws apigatewayv2 get-integration --api-id "${API}" --integration-id "${INT}" --query IntegrationUri --output text)
  RUTA="/${URI#*://*/}"
  aws apigatewayv2 update-integration --api-id "${API}" --integration-id "${INT}" --integration-uri "http://${IP}:8080${RUTA}" >/dev/null
  echo "    ${INT} -> http://${IP}:8080${RUTA}"
done

echo "==> 5/5 Verificando de punta a punta"
API_URL=$(terraform -chdir=terraform output -raw api_url)
for i in $(seq 1 12); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -m 20 "${API_URL}/productos" || echo 000)
  echo "    intento ${i}: GET sin token -> ${CODE}"
  [ "${CODE}" = "401" ] && break
  sleep 10
done
if [ "${CODE}" != "401" ]; then
  echo "ERROR: el gateway no devuelve 401; el backend no esta alcanzable."
  exit 1
fi

echo
echo "LISTO. El sistema responde."
echo "  API      : ${API_URL}"
echo "  Front    : $(terraform -chdir=terraform output -raw amplify_url)"
echo "  Backend  : http://${IP}:8080"
echo
echo "Si vas a demostrar una pipeline, actualiza los 3 secretos en GitHub"
echo "(Settings -> Secrets and variables -> Actions) con las credenciales nuevas."
