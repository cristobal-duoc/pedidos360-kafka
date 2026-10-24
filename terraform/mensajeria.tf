# ---------- Los dos microservicios de mensajeria, en ECS Fargate como el backend ----------
#   notificaciones : los consumidores (avisos, comprobantes, auditoria) con sus DLQ.
#   rabbit-admin   : la API REST que crea y elimina colas, exchanges y bindings.
# Los dos se conectan al cluster de rabbitmq.tf por la red privada.

locals {
  microservicios_mensajeria = {
    notificaciones = { puerto = null, grupo = aws_security_group.notificaciones.id, env = local.env_rabbitmq }
    rabbit-admin   = { puerto = 8097, grupo = aws_security_group.rabbit_admin.id, env = concat(local.env_rabbitmq, local.env_cognito) }
  }
}

# notificaciones no recibe conexiones: solo sale hacia RabbitMQ y ECR.
resource "aws_security_group" "notificaciones" {
  name        = "pedidos360-notificaciones-${var.estudiante}"
  description = "Sin entrada: solo consume de RabbitMQ"
  vpc_id      = data.aws_vpc.default.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# rabbit-admin expone su API en el 8097. Como el backend, valida el JWT de Cognito
# por su cuenta: llegar a la IP no sirve de nada sin un token del rol aprobadores.
resource "aws_security_group" "rabbit_admin" {
  name        = "pedidos360-rabbit-admin-${var.estudiante}"
  description = "8097 para la API de administracion de RabbitMQ"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 8097
    to_port     = 8097
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_ecr_repository" "mensajeria" {
  for_each     = local.microservicios_mensajeria
  name         = "dsy1107-${each.key}-${var.estudiante}"
  force_delete = true
}

resource "aws_cloudwatch_log_group" "mensajeria" {
  for_each          = local.microservicios_mensajeria
  name              = "/ecs/dsy1107-${each.key}-${var.estudiante}"
  retention_in_days = 7
}

resource "aws_ecs_task_definition" "mensajeria" {
  for_each                 = local.microservicios_mensajeria
  family                   = "dsy1107-${each.key}-${var.estudiante}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = "arn:aws:iam::${data.aws_caller_identity.yo.account_id}:role/LabRole"

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = each.key
    image        = "${aws_ecr_repository.mensajeria[each.key].repository_url}:latest"
    portMappings = each.value.puerto == null ? [] : [{ containerPort = each.value.puerto }]
    environment  = each.value.env
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.mensajeria[each.key].name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = each.key
      }
    }
  }])
}

resource "aws_ecs_service" "mensajeria" {
  for_each        = local.microservicios_mensajeria
  name            = each.key
  cluster         = aws_ecs_cluster.backend.id
  task_definition = aws_ecs_task_definition.mensajeria[each.key].arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.publicas.ids
    security_groups  = [each.value.grupo]
    assign_public_ip = true # sin NAT, la IP publica es la salida para bajar la imagen de ECR
  }
}
