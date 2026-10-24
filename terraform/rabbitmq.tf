# ---------- El cluster de RabbitMQ: tres nodos con docker compose en una EC2 ----------
# Los tres nodos corren en la misma instancia, como en la clase practica 2.3.1:
# el cluster tolera la caida de un NODO (proceso RabbitMQ), no la de la maquina.

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

# Una de las zonas de us-east-1 no ofrece t3: la EC2 va en una subred de una zona que si.
data "aws_ec2_instance_type_offerings" "t3_medium" {
  location_type = "availability-zone"
  filter {
    name   = "instance-type"
    values = ["t3.medium"]
  }
}

data "aws_subnets" "con_t3_medium" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
  filter {
    name   = "availability-zone"
    values = data.aws_ec2_instance_type_offerings.t3_medium.locations
  }
}

locals {
  rabbit_usuario = "admin"
  # Se reutiliza db_pass como clave, igual que los usuarios de prueba de Cognito.
  rabbit_clave = var.db_pass

  # Los tres puertos AMQP de la EC2 (5672, 5673, 5674), uno por nodo.
  rabbit_addresses = join(",", [for p in [5672, 5673, 5674] : "${aws_instance.rabbitmq.private_ip}:${p}"])

  # Con que validan el JWT el backend y rabbit-admin. Salen del pool que crea
  # Terraform: si el lab se reinicia y Cognito se recrea con otros ids, se actualizan solos.
  env_cognito = [
    { name = "COGNITO_ISSUER_URI", value = "https://${aws_cognito_user_pool.pool.endpoint}" },
    { name = "COGNITO_CLIENT_ID", value = aws_cognito_user_pool_client.spa.id },
  ]

  # Lo mismo que necesita cada microservicio para conectarse al cluster.
  env_rabbitmq = [
    { name = "RABBITMQ_ADDRESSES", value = local.rabbit_addresses },
    { name = "RABBITMQ_USER", value = local.rabbit_usuario },
    { name = "RABBITMQ_PASS", value = local.rabbit_clave },
  ]
}

resource "aws_security_group" "rabbitmq" {
  name        = "pedidos360-rabbitmq-${var.estudiante}"
  description = "AMQP solo desde los microservicios; paneles y SSH para administrar"
  vpc_id      = data.aws_vpc.default.id

  # AMQP (5672-5674): solo los tres microservicios de ECS, por la red privada.
  ingress {
    from_port = 5672
    to_port   = 5674
    protocol  = "tcp"
    security_groups = [
      aws_security_group.tarea.id,
      aws_security_group.notificaciones.id,
      aws_security_group.rabbit_admin.id,
    ]
  }
  # Paneles de administracion de los tres nodos (15672-15674): se entra con admin y db_pass.
  ingress {
    from_port   = 15672
    to_port     = 15674
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  # SSH, para entrar por EC2 Instance Connect y mostrar docker compose en vivo.
  ingress {
    from_port   = 22
    to_port     = 22
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

resource "aws_instance" "rabbitmq" {
  ami                         = data.aws_ami.amazon_linux.id
  instance_type               = "t3.medium" # 4 GB: tres nodos RabbitMQ
  subnet_id                   = sort(data.aws_subnets.con_t3_medium.ids)[0]
  vpc_security_group_ids      = [aws_security_group.rabbitmq.id]
  associate_public_ip_address = true
  iam_instance_profile        = "LabInstanceProfile" # el perfil que permite el Learner Lab
  key_name                    = "vockey"             # la llave del Learner Lab

  # replace quita los \r por si algun editor de Windows guardo el script con CRLF:
  # en Linux, "#!/bin/bash\r" no existe y la EC2 arrancaria sin cluster.
  user_data = replace(templatefile("${path.module}/rabbitmq-cluster.sh.tftpl", {
    compose = file("${path.module}/../rabbitmq-cluster/docker-compose.yml")
    conf    = file("${path.module}/../rabbitmq-cluster/rabbitmq.conf")
    usuario = local.rabbit_usuario
    clave   = local.rabbit_clave
  }), "\r", "")
  # Si cambia el compose o la configuracion, se recrea la instancia con lo nuevo.
  user_data_replace_on_change = true

  # Amazon publica AMIs nuevas cada pocas semanas: sin esto, el siguiente apply
  # (p. ej. revivir.sh el dia de la presentacion) recrearia la EC2 y el cluster.
  lifecycle {
    ignore_changes = [ami]
  }

  root_block_device {
    volume_size = 20
  }

  tags = {
    Name = "pedidos360-rabbitmq-${var.estudiante}"
  }
}
