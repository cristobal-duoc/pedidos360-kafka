# ---------- La red: se reutiliza la VPC por defecto ----------
data "aws_vpc" "default" { default = true }

data "aws_subnets" "publicas" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "map-public-ip-on-launch"
    values = ["true"]
  }
}

data "aws_internet_gateway" "default" {
  filter {
    name   = "attachment.vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_route_table" "principal" {
  vpc_id = data.aws_vpc.default.id
}

# LA TRAMPA: la VPC por defecto del Learner Lab tiene el internet gateway, pero
# SIN la ruta 0.0.0.0/0. Sin ella la task no baja la imagen de ECR.
resource "aws_route" "salida_a_internet" {
  route_table_id         = data.aws_route_table.principal.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = data.aws_internet_gateway.default.id
}

# ---------- El registro y los logs ----------
resource "aws_ecr_repository" "backend" {
  name         = "dsy1107-backend-${var.estudiante}"
  force_delete = true
  image_scanning_configuration {
    scan_on_push = false
  }
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/dsy1107-backend-${var.estudiante}"
  retention_in_days = 7
}

# Sin balanceador: el API Gateway llama por internet, asi que el 8080 queda
# abierto. No es la vulnerabilidad que parece: el backend valida el JWT por su
# cuenta (401) y exige el rol para escribir (403), asi que llegar a la IP no
# sirve de nada sin un token valido. Con un balanceador el puerto no estaria
# expuesto, pero el Learner Lab no permite crearlo.
resource "aws_security_group" "tarea" {
  name        = "pedidos360-tarea-${var.estudiante}"
  description = "8080 abierto para el API Gateway"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port   = 8080
    to_port     = 8080
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

# ---------- ECS Fargate ----------
resource "aws_ecs_cluster" "backend" {
  name = "dsy1107-backend-${var.estudiante}"
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "dsy1107-backend-${var.estudiante}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = "arn:aws:iam::${data.aws_caller_identity.yo.account_id}:role/LabRole"

  # Fargate corre x86_64; si compilas en Mac ARM, la task muere con exec format error.
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name         = "backend"
    image        = "${aws_ecr_repository.backend.repository_url}:latest"
    portMappings = [{ containerPort = 8080 }]
    environment = concat([
      # sslmode=require NO es opcional: RDS rechaza toda conexion en claro.
      { name = "DB_HOST", value = aws_db_instance.pedidos.address },
      { name = "DB_USER", value = var.db_user },
      { name = "DB_PASS", value = var.db_pass },
      { name = "DB_SSLMODE", value = "require" },
    ], local.env_rabbitmq, local.env_cognito) # mas el cluster de RabbitMQ y Cognito (rabbitmq.tf)
    healthCheck = {
      command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.backend.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "backend"
      }
    }
  }])
}

resource "aws_ecs_service" "backend" {
  name            = "backend"
  cluster         = aws_ecs_cluster.backend.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.publicas.ids
    security_groups  = [aws_security_group.tarea.id]
    assign_public_ip = true
  }

  # El script decide que imagen corre; sin esto el apply revertiria.
  lifecycle {
    ignore_changes = [task_definition]
  }
}
