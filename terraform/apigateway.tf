# El API Manager: la puerta única entre el front y el backend.
resource "aws_apigatewayv2_api" "api" {
  name          = "pedidos360-${var.estudiante}"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = ["http://localhost:5173"] # + la URL de Amplify al desplegar
    allow_methods = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    allow_headers = ["authorization", "content-type"]
    max_age       = 300
  }
}

# Integración HTTP_PROXY hacia el backend. El script reapunta integration_uri a
# la IP real de la task; por eso ignore_changes.
resource "aws_apigatewayv2_integration" "backend" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_method     = "ANY"
  integration_uri        = "${var.backend_url}/productos"
  payload_format_version = "1.0"

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
locals {
  rutas_productos = {
    "GET /productos"    = "productos/read"
    "POST /productos"   = "productos/write"
    "PUT /productos"    = "productos/write"
    "DELETE /productos" = "productos/write"
  }
}

resource "aws_apigatewayv2_route" "productos" {
  for_each             = local.rutas_productos
  api_id               = aws_apigatewayv2_api.api.id
  route_key            = each.key
  target               = "integrations/${aws_apigatewayv2_integration.backend.id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.cognito.id
  authorization_scopes = [each.value]
}
