# El user pool es el "tenant": el directorio de usuarios y el emisor de tokens.
resource "aws_cognito_user_pool" "pool" {
  name                     = "dsy1107-${var.estudiante}"
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  # ESSENTIALS es OBLIGATORIO: el Lambda V2 (que pone los scopes) solo existe
  # desde este tier. Con el tier Lite el token sale sin scopes y sin error.
  user_pool_tier = "ESSENTIALS"

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = false
  }

  # Auto-registro habilitado: el Hosted UI muestra "Sign up" y el usuario crea
  # su cuenta solo, verificandola por codigo al correo (auto_verified_attributes).
  # La rubrica pide exactamente eso; con true el enlace no aparece.
  admin_create_user_config {
    allow_admin_create_user_only = false
  }

  # El trigger que traduce grupos -> scopes, antes de firmar el token.
  lambda_config {
    pre_token_generation_config {
      lambda_arn     = aws_lambda_function.user_token_ms.arn
      lambda_version = "V2_0"
    }
  }
}

# El dominio del Hosted UI y los endpoints /oauth2/*.
resource "aws_cognito_user_pool_domain" "hosted_ui" {
  domain                = "dsy1107-${var.estudiante}"
  user_pool_id          = aws_cognito_user_pool.pool.id
  managed_login_version = 1 # Hosted UI clásica
}

# El cliente PÚBLICO (sin secreto): su código vive en el navegador, por eso PKCE.
resource "aws_cognito_user_pool_client" "spa" {
  name         = "spa-react"
  user_pool_id = aws_cognito_user_pool.pool.id

  generate_secret                      = false
  allowed_oauth_flows_user_pool_client = true
  allowed_oauth_flows                  = ["code"]
  supported_identity_providers         = ["COGNITO"]

  # OJO: productos/read y productos/write NO van aquí. Solo el Lambda los concede.
  allowed_oauth_scopes = ["openid", "email", "profile"]

  # localhost para desarrollo y Amplify para el despliegue real. Cognito solo
  # redirige a URLs declaradas aqui: si falta, el login muerte con redirect_mismatch.
  callback_urls = ["http://localhost:5173/", "${local.url_amplify}/"]
  logout_urls   = ["http://localhost:5173/", "${local.url_amplify}/"]

  explicit_auth_flows = ["ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]

  access_token_validity = 60
  id_token_validity     = 60
  token_validity_units {
    access_token = "minutes"
    id_token     = "minutes"
  }
}

# El resource server DECLARA qué scopes existen (no los concede a nadie).
resource "aws_cognito_resource_server" "solicitudes" {
  identifier   = "solicitudes"
  name         = "API de solicitudes"
  user_pool_id = aws_cognito_user_pool.pool.id

  scope {
    scope_name        = "read"
    scope_description = "Consultar solicitudes"
  }
  scope {
    scope_name        = "write"
    scope_description = "Crear, editar y eliminar solicitudes propias"
  }
  scope {
    scope_name        = "approve"
    scope_description = "Aprobar o rechazar solicitudes"
  }
}

# Los grupos: aquí NACE el permiso de cada persona. Son dos papeles distintos y
# NINGUNO tiene todos los scopes: el solicitante no aprueba, el aprobador no crea.
resource "aws_cognito_user_group" "solicitantes" {
  name         = "solicitantes"
  user_pool_id = aws_cognito_user_pool.pool.id
  description  = "Crea y edita sus solicitudes (solicitudes/read + write)"
}

resource "aws_cognito_user_group" "aprobadores" {
  name         = "aprobadores"
  user_pool_id = aws_cognito_user_pool.pool.id
  description  = "Aprueba o rechaza solicitudes (solicitudes/read + approve)"
}

# Dos usuarios de prueba, uno por papel, ya confirmados.
resource "aws_cognito_user" "solicitante" {
  user_pool_id   = aws_cognito_user_pool.pool.id
  username       = "alumno@duocuc.cl"
  password       = var.db_pass # reutilizamos una clave que cumple la política
  message_action = "SUPPRESS"

  attributes = {
    email          = "alumno@duocuc.cl"
    email_verified = "true"
  }
}

resource "aws_cognito_user" "aprobador" {
  user_pool_id   = aws_cognito_user_pool.pool.id
  username       = "jefatura@duocuc.cl"
  password       = var.db_pass
  message_action = "SUPPRESS"

  attributes = {
    email          = "jefatura@duocuc.cl"
    email_verified = "true"
  }
}

resource "aws_cognito_user_in_group" "el_solicitante" {
  user_pool_id = aws_cognito_user_pool.pool.id
  group_name   = aws_cognito_user_group.solicitantes.name
  username     = aws_cognito_user.solicitante.username
}

resource "aws_cognito_user_in_group" "el_aprobador" {
  user_pool_id = aws_cognito_user_pool.pool.id
  group_name   = aws_cognito_user_group.aprobadores.name
  username     = aws_cognito_user.aprobador.username
}
