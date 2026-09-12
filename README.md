# Pedidos360 · DSY1107 Cloud Native I

Sistema full-stack seguro en AWS para **solicitudes de vacaciones**, con dos
papeles: el **solicitante** pide y gestiona sus solicitudes, y el **aprobador**
las resuelve dejando un comentario.

Un front React inicia sesión con **Cognito** (OIDC, Authorization Code + PKCE),
un **API Gateway** valida el JWT y autoriza por scope, un backend **Spring Boot**
en **ECS Fargate** vuelve a validar el token y ejecuta la lógica, y los datos
viven en **PostgreSQL** sobre RDS. Todo levantado con **Terraform**.

## Arquitectura (el permiso viaja en el token)

```
Usuario → Front (React/Amplify) → Cognito ─(login PKCE)→ tokens
   grupo del usuario → Lambda user-token-ms → claim scope → access token
Front → API Gateway ─(valida JWT + authorization_scopes)→ Spring Boot (ECS) → RDS
   401 = sin token o inválido      403 = token válido sin el scope de la ruta
```

- **Autenticar** (¿quién eres?) lo hacen los dos, cada uno por su cuenta: el
  gateway con su autorizador JWT y el backend como Resource Server. Sin token
  válido, **401**.
- **Autorizar** (¿qué puedes?) también los dos. La compuerta primaria es el
  gateway, que exige el scope de la ruta y corta antes de gastar el contenedor.
  El backend además exige el rol con `@PreAuthorize`, por si alguien lo llamara
  directo por su IP saltándose el gateway. Sin permiso, **403**.

## Los dos papeles

| Grupo de Cognito | Scopes que recibe | Puede |
|:-----------------|:------------------|:------|
| `solicitantes`   | `solicitudes/read` + `solicitudes/write`   | crear, editar y eliminar sus solicitudes |
| `aprobadores`    | `solicitudes/read` + `solicitudes/approve` | aprobar o rechazar, con comentario |

**Ningún grupo tiene `write` y `approve` a la vez**: ahí está la separación de
papeles. Los scopes no se piden desde el front — los concede la Lambda
`user-token-ms` según el grupo, justo antes de que Cognito firme el token.

## Las rutas

| Método y ruta                     | Scope exigido         | Papel |
|:----------------------------------|:----------------------|:------|
| `GET /solicitudes`                | `solicitudes/read`    | los dos |
| `POST /solicitudes`               | `solicitudes/write`   | solicitante |
| `PUT /solicitudes/{id}`           | `solicitudes/write`   | solicitante |
| `DELETE /solicitudes/{id}`        | `solicitudes/write`   | solicitante |
| `PUT /solicitudes/{id}/decision`  | `solicitudes/approve` | aprobador |

Son **tres integraciones** en el gateway, no una: una integración HTTP_PROXY
apunta a un path fijo, y `/solicitudes`, `/solicitudes/{id}` y
`/solicitudes/{id}/decision` son tres paths distintos.

Dos reglas que el backend impone y no se pueden saltar desde el cliente: el
`solicitante` se toma del token (nadie pide vacaciones a nombre de otro) y el
`estado` nace siempre `PENDIENTE`. Una solicitud ya resuelta responde **409**.

## Estructura

```
.github/workflows/   frontend_compile · frontend_deploy · backend_compile · backend_deploy
backend/             Spring Boot (Java 21, Maven) + Dockerfile
frontend/            React + Vite
terraform/           cognito · lambda · apigateway · rds · ecs · amplify
scripts/             publicar-ecs.sh · revivir.sh
```

## Levantarlo

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

Si el lab se apagó y el código no cambió, basta con `scripts/revivir.sh`: repone
la infraestructura, espera a la task, reapunta las integraciones del gateway a su
IP nueva y comprueba que responda 401 antes de darse por exitoso. No reconstruye
la imagen, que ya está en ECR.

## Usuarios de prueba

| Usuario | Papel |
|:--------|:------|
| `alumno@duocuc.cl`    | solicitantes |
| `jefatura@duocuc.cl`  | aprobadores |

Ambos con la contraseña definida en `db_pass`. Entra con uno y con otro para ver
cómo cambia la interfaz: al aprobador ni siquiera se le muestra el formulario de
creación, y al solicitante no se le muestran los botones de resolución. Esa es
solo la capa de comodidad; el **403** lo imponen el gateway y el backend.

## Qué nunca se versiona

`terraform.tfstate`, `*.tfvars`, `.env.local`, `node_modules/`, `dist/`, `target/`
(cubiertos por `.gitignore`). El `.terraform.lock.hcl` **sí** se versiona.
