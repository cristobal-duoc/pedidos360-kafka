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
variable "backend_url" {
  type    = string
  default = "http://backend-todavia-no-desplegado:8080"
}
