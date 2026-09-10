terraform {
  required_version = ">= 1.5"
  required_providers {
    aws     = { source = "hashicorp/aws", version = "~> 5.100" }
    archive = { source = "hashicorp/archive", version = "~> 2.4" }
  }
}

provider "aws" {
  region = var.aws_region
}

# Datos de la cuenta/region, usados por lambda.tf y ecs.tf (ARN de LabRole).
data "aws_caller_identity" "yo" {}
