# El API Manager: la puerta única entre el front y el backend.
resource "aws_apigatewayv2_api" "api" {
  name          = "pedidos360-${var.estudiante}"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = ["http://localhost:5173", local.url_amplify]
    allow_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allow_headers = ["authorization", "content-type"]
    max_age       = 300
  }
}

# Tres integraciones HTTP_PROXY, una por cada path del backend:
#   /solicitudes                -> listar y crear
#   /solicitudes/{id}           -> editar y borrar
#   /solicitudes/{id}/decision  -> aprobar o rechazar
# Una integracion HTTP_PROXY apunta a un path FIJO, asi que no se pueden
# compartir: con una sola, las rutas con {id} devolverian 404.
# Los scripts reapuntan integration_uri a la IP real de la task conservando el
# path de cada una; por eso el ignore_changes.
resource "aws_apigatewayv2_integration" "coleccion" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = "${var.backend_url}/solicitudes"
  payload_format_version = "1.0"

  lifecycle {
    ignore_changes = [integration_uri]
  }
}

resource "aws_apigatewayv2_integration" "item" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = "${var.backend_url}/solicitudes/{id}"
  payload_format_version = "1.0"

  request_parameters = {
    "overwrite:path" = "/solicitudes/$request.path.id"
  }

  lifecycle {
    ignore_changes = [integration_uri]
  }
}

resource "aws_apigatewayv2_integration" "decision" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = "${var.backend_url}/solicitudes/{id}/decision"
  payload_format_version = "1.0"

  request_parameters = {
    "overwrite:path" = "/solicitudes/$request.path.id/decision"
  }

  lifecycle {
    ignore_changes = [integration_uri]
  }
}

# El autorizador JWT: valida firma (JWKS), issuer, audience y expiración.
resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.api.id
  name             = "cognito-jwt"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  jwt_configuration {
    # El access token de Cognito trae client_id, no aud: el authorizer lo compara.
    audience = [aws_cognito_user_pool_client.spa.id]
    issuer   = "https://${aws_cognito_user_pool.pool.endpoint}"
  }
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.api.id
  name        = "$default"
  auto_deploy = true
}

# La POLÍTICA de acceso: método -> scope exigido. Aquí vive la autorización.
# Leer lo puede hacer cualquiera de los dos papeles; crear y editar exige
# solicitudes/write (solicitantes) y resolver exige solicitudes/approve
# (aprobadores). Ningun grupo tiene los dos ultimos.
locals {
  rutas_coleccion = {
    "GET /solicitudes"  = "solicitudes/read"
    "POST /solicitudes" = "solicitudes/write"
  }
  rutas_item = {
    "PUT /solicitudes/{id}"    = "solicitudes/write"
    "DELETE /solicitudes/{id}" = "solicitudes/write"
  }
}

resource "aws_apigatewayv2_route" "coleccion" {
  for_each             = local.rutas_coleccion
  api_id               = aws_apigatewayv2_api.api.id
  route_key            = each.key
  target               = "integrations/${aws_apigatewayv2_integration.coleccion.id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.cognito.id
  authorization_scopes = [each.value]
}

resource "aws_apigatewayv2_route" "item" {
  for_each             = local.rutas_item
  api_id               = aws_apigatewayv2_api.api.id
  route_key            = each.key
  target               = "integrations/${aws_apigatewayv2_integration.item.id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.cognito.id
  authorization_scopes = [each.value]
}

# La ruta que separa los dos papeles: solo el aprobador la puede llamar.
resource "aws_apigatewayv2_route" "decision" {
  api_id               = aws_apigatewayv2_api.api.id
  route_key            = "PUT /solicitudes/{id}/decision"
  target               = "integrations/${aws_apigatewayv2_integration.decision.id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.cognito.id
  authorization_scopes = ["solicitudes/approve"]
}
