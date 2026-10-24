output "cognito_user_pool_id" {
  value = aws_cognito_user_pool.pool.id
}

output "cognito_client_id" {
  value = aws_cognito_user_pool_client.spa.id
}

output "api_url" {
  description = "Base del API Gateway"
  value       = aws_apigatewayv2_api.api.api_endpoint
}

output "amplify_app_id" {
  description = "Lo necesita aws amplify create-deployment"
  value       = aws_amplify_app.front.id
}

output "amplify_url" {
  description = "Va en callback_urls, logout_urls y CORS"
  value       = local.url_amplify
}

output "ecr_url" {
  value = aws_ecr_repository.backend.repository_url
}

# Listo para: terraform output -raw env_frontend > ../frontend/.env.local
output "env_frontend" {
  value = <<-ENV
    VITE_AWS_REGION=${var.aws_region}
    VITE_COGNITO_DOMAIN=https://${aws_cognito_user_pool_domain.hosted_ui.domain}.auth.${var.aws_region}.amazoncognito.com
    VITE_COGNITO_CLIENT_ID=${aws_cognito_user_pool_client.spa.id}
    VITE_REDIRECT_URI=http://localhost:5173/
    VITE_API_URL=${aws_apigatewayv2_api.api.api_endpoint}
  ENV
}

# ---------- RabbitMQ ----------
output "rabbitmq_paneles" {
  description = "Los paneles de los tres nodos (usuario admin, clave db_pass)"
  value       = [for p in [15672, 15673, 15674] : "http://${aws_instance.rabbitmq.public_ip}:${p}"]
}

output "rabbitmq_ec2_id" {
  description = "Para entrar por EC2 Instance Connect y ver docker compose"
  value       = aws_instance.rabbitmq.id
}
