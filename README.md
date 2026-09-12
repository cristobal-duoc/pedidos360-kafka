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

- **Autenticar** (¿quién eres?) lo hacen los dos, cada uno por su cuenta: el
  gateway con su autorizador JWT y el backend como Resource Server. Sin token
  válido, **401**.
- **Autorizar** (¿qué puedes?) también los dos. La compuerta primaria es el
  gateway, que exige el scope de la ruta (`authorization_scopes`) y corta antes
  de gastar el contenedor. El backend además exige el rol `editores` para
  escribir (`@PreAuthorize`), por si alguien lo llamara directo por su IP
  saltándose el gateway. Sin permiso, **403**.

## Estructura

```
.github/workflows/   frontend_compile · frontend_deploy · backend_compile · backend_deploy
backend/             Spring Boot (Java 21, Maven) + Dockerfile
frontend/            React + Vite
terraform/           cognito · lambda · apigateway · rds · ecs · amplify
scripts/             publicar-ecs.sh · revivir.sh
```

## Las rutas

| Método y ruta            | Scope exigido      | Rol       |
|:-------------------------|:-------------------|:----------|
| `GET /productos`         | `productos/read`   | cualquiera autenticado |
| `POST /productos`        | `productos/write`  | `editores` |
| `PUT /productos/{id}`    | `productos/write`  | `editores` |
| `DELETE /productos/{id}` | `productos/write`  | `editores` |

Son dos integraciones en el gateway, no una: una integración HTTP_PROXY apunta a
un path fijo, y `/productos` no es lo mismo que `/productos/{id}`.

## Levantarlo (resumen)

Pulsa **Start Lab** en AWS Academy y copia las credenciales. Luego:

```bash
cp terraform/terraform.tfvars.example terraform/terraform.tfvars   # pon tu db_pass y estudiante
cd terraform && terraform init && terraform apply                  # crea todo (5-10 min)
terraform output -raw env_frontend > ../frontend/.env.local        # config del front
cd .. && scripts/publicar-ecs.sh                                   # publica el backend en ECS
```

Si el lab se apagó y el código no cambió, basta con `scripts/revivir.sh`: repone
la infraestructura, espera a la task, reapunta las dos integraciones del gateway
a su IP nueva y comprueba que responda 401 antes de darse por exitoso. No
reconstruye la imagen, que ya está en ECR.

Configura el backend con tus valores de Cognito en
`backend/src/main/resources/application.properties` (issuer-uri y client-id, de
`terraform output`). El front local: `cd frontend && npm install && npm run dev`.

Usuario de prueba: `alumno@duocuc.cl` (grupo `lectores`). Muévelo a `editores`
y vuelve a iniciar sesión para demostrar el paso de **403** a **200**: el permiso
viaja firmado dentro del token, así que el token anterior sigue sin poder
escribir.

## Qué nunca se versiona

`terraform.tfstate`, `*.tfvars`, `.env.local`, `node_modules/`, `dist/`, `target/`
(cubiertos por `.gitignore`). El `.terraform.lock.hcl` **sí** se versiona.
