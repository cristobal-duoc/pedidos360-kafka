# Pedidos360 · DSY1107 Cloud Native I · EA1

Sistema full-stack seguro en AWS: front React que inicia sesión con **Cognito**
(OIDC, Authorization Code + PKCE), un **API Gateway** que valida el JWT y autoriza
por scope, un backend **Spring Boot** en **ECS Fargate** que valida el token y hace
CRUD, y **PostgreSQL** en RDS. Todo con **Terraform**.

## Arquitectura (el permiso viaja en el token)

```
Usuario → Front (React/Amplify) → Cognito ─(login PKCE)→ tokens
   grupo del usuario → Lambda user-token-ms → claim scope → access token
Front → API Gateway ─(valida JWT + authorization_scopes)→ Spring Boot (ECS) → RDS
   401 = sin token/ inválido    403 = token válido sin el scope de la ruta
```

- **Autenticar** (¿quién eres?) lo hace el backend (Resource Server) y el gateway.
- **Autorizar** (¿qué puedes?) lo hace el API Gateway con `authorization_scopes`.
  El backend no tiene lógica de permisos.

## Estructura

```
.github/workflows/   frontend_compile · frontend_deploy · backend_compile · backend_deploy
backend/             Spring Boot (Java 21, Maven) + Dockerfile
frontend/            React + Vite
terraform/           cognito · lambda · apigateway · rds · ecs · amplify
scripts/             publicar-ecs.sh
```

## Levantarlo (resumen)

Pulsa **Start Lab** en AWS Academy y copia las credenciales. Luego:

```bash
cp terraform/terraform.tfvars.example terraform/terraform.tfvars   # pon tu db_pass y estudiante
cd terraform && terraform init && terraform apply                  # crea todo (5-10 min)
terraform output -raw env_frontend > ../frontend/.env.local        # config del front
cd .. && scripts/publicar-ecs.sh                                   # publica el backend en ECS
```

Configura el backend con tus valores de Cognito en
`backend/src/main/resources/application.properties` (issuer-uri y client-id, de
`terraform output`). El front local: `cd frontend && npm install && npm run dev`.

Usuario de prueba: `alumno@duocuc.cl` (grupo `lectores`). Cámbialo a `editores`
para demostrar el paso de 403 a 201.

## Qué nunca se versiona

`terraform.tfstate`, `*.tfvars`, `.env.local`, `node_modules/`, `dist/`, `target/`
(cubiertos por `.gitignore`). El `.terraform.lock.hcl` **sí** se versiona.
