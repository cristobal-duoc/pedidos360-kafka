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

# Dos integraciones HTTP_PROXY, porque los paths del backend son distintos:
#   /productos        -> listar y crear
#   /productos/{id}   -> actualizar y borrar
# Con una sola, las rutas con {id} darian 404: el gateway llamaria siempre a
# /productos y Spring no encontraria el metodo.
# Los scripts reapuntan integration_uri a la IP real de la task conservando el
# path de cada una; por eso el ignore_changes.
resource "aws_apigatewayv2_integration" "coleccion" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = "${var.backend_url}/productos"
  payload_format_version = "1.0"

  lifecycle {
    ignore_changes = [integration_uri]
  }
}

resource "aws_apigatewayv2_integration" "item" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  # {id} se sustituye con el parametro de la ruta.
  integration_uri        = "${var.backend_url}/productos/{id}"
  payload_format_version = "1.0"

  request_parameters = {
    "overwrite:path" = "/productos/$request.path.id"
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
# Leer exige productos/read; cualquier escritura exige productos/write.
locals {
  rutas_coleccion = {
    "GET /productos"  = "productos/read"
    "POST /productos" = "productos/write"
  }
  rutas_item = {
    "PUT /productos/{id}"    = "productos/write"
    "DELETE /productos/{id}" = "productos/write"
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
