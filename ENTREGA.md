# Checklist de entrega · Pedidos360 (EP1 + EP2)

> Marca cada `[ ]` a medida que lo completes. Todo comando de Terraform se corre
> desde `terraform/`. **Pulsa Start Lab en AWS Academy y copia tus credenciales
> antes de cada sesión** (se vencen).


## Datos reales del despliegue (12-09-2026)

| Qué | Valor |
|---|---|
| API Gateway | `https://mnljt0cdw0.execute-api.us-east-1.amazonaws.com` |
| Cognito user pool | `us-east-1_jV63RQ8jF` |
| Client ID (SPA) | `5rmbpaeusn73rpqcj6dt3m6l59` |
| Dominio Cognito | `https://dsy1107-martinez.auth.us-east-1.amazoncognito.com` |
| Amplify | `https://main.d3tixpf7a11ce.amplifyapp.com` |
| Usuario demo | `alumno@duocuc.cl` (grupos: lectores + editores) |

> La IP pública de la task de ECS **cambia en cada redespliegue**. No la anotes:
> `scripts/publicar-ecs.sh` reapunta el API Gateway solo. Y como el lab se apaga,
> hay que volver a correr `terraform apply` + el script en cada sesión.

## Fase A · Ya hecho

- [x] Código en GitHub (`cristobal-duoc/pedidos360-ea1`, privado, rama `main`)
- [x] Estructura correcta: `backend/ frontend/ terraform/ scripts/ .github/workflows/`
- [x] Verificado en local: `terraform validate`, `npm run build`, `mvn clean package`

## Fase B · Rellenar los 3 placeholders (a mano)

- [x] **Tu apellido** en 3 sitios (reemplaza `martinez`):
  - [x] `.github/workflows/backend_deploy.yml` → `ESTUDIANTE: <apellido>`
  - [x] `.github/workflows/frontend_deploy.yml` → `ESTUDIANTE: <apellido>`
  - [x] `scripts/publicar-ecs.sh` → `ESTUDIANTE="<apellido>"`
- [x] `terraform/terraform.tfvars` (copia de `.example`, NO se versiona):
  - [x] `estudiante = "<apellido>"` y `db_pass = "<tu clave>"`
- [x] `backend/src/main/resources/application.properties` (después del `apply`, Fase C):
  - [x] `issuer-uri` → con tu `terraform output cognito_user_pool_id`
  - [x] `cognito.client-id` → con tu `terraform output cognito_client_id`

## Fase C · Crear la infraestructura en AWS

- [x] `terraform init`
- [x] `terraform plan`  → revisa que diga **0 to destroy**
- [x] `terraform apply`  (tarda 5–10 min; manda RDS, no lo interrumpas)
- [x] `terraform output -raw env_frontend > ../frontend/.env.local`

## Fase D · Publicar el backend y el front

- [x] Backend: `scripts/publicar-ecs.sh` (compila el .jar, sube la imagen a ECR,
      redespliega en ECS y reapunta el API Gateway a la IP de la task)
- [x] Asignar tu usuario a un grupo (para probar el 403):
      `aws cognito-idp admin-add-user-to-group --user-pool-id <POOL> --username alumno@duocuc.cl --group-name lectores`
- [ ] Front en Amplify: recompila con la URL de Amplify y sube el zip; añade esa
      URL a `callback_urls`, `logout_urls` (cognito.tf) y `allow_origins` (apigateway.tf)

## Fase E · Las 4 pipelines EN VERDE

- [ ] Crear los 3 secretos en GitHub → Settings → Secrets and variables → Actions:
      `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`
- [ ] `frontend_compile` en verde (corre solo al hacer push)
- [ ] `backend_compile` en verde (corre solo al hacer push)
- [ ] `frontend_deploy` ejecutada al menos una vez → verde (Actions → Run workflow)
- [ ] `backend_deploy` ejecutada al menos una vez → verde
- [ ] Todo integrado en `main` (no en ramas sueltas)

## Fase F · Verificación end-to-end (los tres números)

- [x] `GET /productos` **sin** token → **401**
- [x] `GET /productos` con token de lector → **200**
- [x] `POST /productos` con token de lector → **403**
- [x] Mover el usuario a `editores`, re-loguear, `POST /productos` → **201/200**
- [x] El servicio ECS con la task en **RUNNING** y el health check verde

## Fase G · Entregar EP1 (formal)

- [ ] Copiar el link del repo (`https://github.com/cristobal-duoc/pedidos360-ea1`) a **AVA**
- [ ] Enviar el link por **correo al docente**
- [ ] Repo privado con el docente agregado como colaborador (o público)

## Fase H · Presentación EP2 (5–10 min, en vivo en la nube)

- [ ] El API Manager creado y en funcionamiento (consola AWS)
- [ ] El front (Amplify) consumiendo la API a través del API Gateway
- [ ] El API Gateway validando JWT: **200 / 401 / 403** coherentes
- [ ] El tenant en Cognito con usuarios y grupos (lectores/editores)
- [ ] El login OIDC + PKCE: en DevTools, el `code_challenge` en `/authorize` y el
      `code_verifier` en `/token`; el claim `scope` en el access token
- [ ] Backend y front desplegados, activos e integrados en la nube

---

### Recordatorios clave para la defensa
- **Autenticar** (¿quién eres?) = token válido → 401 si no. Lo hace el backend y el gateway.
- **Autorizar** (¿qué puedes?) = scope de la ruta → 403 si no. Lo hace el API Gateway.
- El permiso: **grupo → Lambda (user-token-ms) → scope → authorization_scopes**.
- El backend NO tiene lógica de permisos: la autorización está en `apigateway.tf`.
