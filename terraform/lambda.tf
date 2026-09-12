# Terraform arma el zip a partir de la carpeta user-token-ms/. Editar el .mjs
# basta para que el siguiente apply lo redespliegue.
data "archive_file" "user_token_ms" {
  type        = "zip"
  source_dir  = "${path.module}/user-token-ms"
  output_path = "${path.module}/user-token-ms.zip"
}

resource "aws_lambda_function" "user_token_ms" {
  function_name    = "user-token-ms-${var.estudiante}"
  filename         = data.archive_file.user_token_ms.output_path
  source_code_hash = data.archive_file.user_token_ms.output_base64sha256
  handler          = "index.handler"
  runtime          = "nodejs20.x"
  role             = "arn:aws:iam::${data.aws_caller_identity.yo.account_id}:role/LabRole"
}

# CRÍTICO: sin este permiso, Cognito no puede invocar el Lambda y el login
# falla en silencio (sin mensaje de error).
resource "aws_lambda_permission" "cognito_invoca" {
  statement_id  = "AllowCognitoInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.user_token_ms.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.pool.arn
}

# ---------- Post Confirmation: quien se registra entra a "solicitantes" ----------
data "archive_file" "user_signup_ms" {
  type        = "zip"
  source_dir  = "${path.module}/user-signup-ms"
  output_path = "${path.module}/user-signup-ms.zip"
}

resource "aws_lambda_function" "user_signup_ms" {
  function_name    = "user-signup-ms-${var.estudiante}"
  filename         = data.archive_file.user_signup_ms.output_path
  source_code_hash = data.archive_file.user_signup_ms.output_base64sha256
  handler          = "index.handler"
  runtime          = "nodejs20.x"
  role             = "arn:aws:iam::${data.aws_caller_identity.yo.account_id}:role/LabRole"
}

resource "aws_lambda_permission" "cognito_invoca_signup" {
  statement_id  = "AllowCognitoInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.user_signup_ms.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.pool.arn
}
