variable "aws_region" {
  type    = string
  default = "us-east-1"
}

# Tu apellido en minúsculas, p. ej. "martinez". Hace únicos los nombres.
variable "estudiante" {
  type = string
}

variable "db_user" {
  type    = string
  default = "postgres"
}

variable "db_pass" {
  type      = string
  sensitive = true
}

# La URL pública del backend en ECS. Terraform la deja en un placeholder y el
# script scripts/publicar-ecs.sh la reapunta a la IP real de la task (por eso
# la integración lleva ignore_changes = [integration_uri]).
# 192.0.2.1 es del rango reservado para documentación (RFC 5737): no apunta a
# nada real, pero API Gateway sí lo acepta como URI válida.
variable "backend_url" {
  type    = string
  default = "http://192.0.2.1:8080"
}
