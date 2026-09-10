# Grupo de seguridad de la base: solo el backend (ECS) puede entrar al 5432.
resource "aws_security_group" "db" {
  name        = "pedidos360-db-${var.estudiante}"
  description = "Solo el backend puede conectar a Postgres"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.tarea.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "pedidos" {
  identifier             = "pedidos360-${var.estudiante}"
  engine                 = "postgres"
  engine_version         = "17"
  instance_class         = "db.t3.micro"
  allocated_storage      = 20
  db_name                = "pedidos360"
  username               = var.db_user
  password               = var.db_pass
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  skip_final_snapshot    = true
}
