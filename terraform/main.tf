terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# --- VPC & Networking ---
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "${var.environment}-payment-platform-vpc"
    Environment = var.environment
  }
}

resource "aws_subnet" "public_1" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.1.0/24"
  availability_zone       = "${var.aws_region}a"
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.environment}-public-subnet-1"
  }
}

resource "aws_subnet" "private_1" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.10.0/24"
  availability_zone = "${var.aws_region}a"

  tags = {
    Name = "${var.environment}-private-subnet-1"
  }
}

# --- Amazon RDS PostgreSQL for Payment & Ledger Data ---
resource "aws_db_subnet_group" "rds_subnet_group" {
  name       = "${var.environment}-rds-subnet-group"
  subnet_ids = [aws_subnet.private_1.id]

  tags = {
    Name = "RDS Database Subnet Group"
  }
}

resource "aws_db_instance" "postgres" {
  identifier             = "${var.environment}-banking-postgres"
  allocated_storage      = 20
  max_allocated_storage  = 100
  engine                 = "postgres"
  engine_version         = "16.1"
  instance_class         = "db.t4g.medium"
  db_name                = "payment_db"
  username               = "dbadmin"
  password               = var.db_password
  skip_final_snapshot    = true
  db_subnet_group_name   = aws_db_subnet_group.rds_subnet_group.name

  tags = {
    Name        = "${var.environment}-banking-rds"
    Environment = var.environment
  }
}

# --- Amazon ElastiCache Redis (for Distributed Locks & Idempotency) ---
resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "${var.environment}-banking-redis"
  engine               = "redis"
  node_type            = "cache.t4g.medium"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379

  tags = {
    Name        = "${var.environment}-banking-cache"
    Environment = var.environment
  }
}

# --- Amazon EKS Cluster (for Microservices Orchestration) ---
resource "aws_eks_cluster" "platform_cluster" {
  name     = "${var.environment}-payment-eks-cluster"
  role_arn = var.eks_role_arn

  vpc_config {
    subnet_ids = [aws_subnet.private_1.id, aws_subnet.public_1.id]
  }

  tags = {
    Name        = "${var.environment}-payment-eks-cluster"
    Environment = var.environment
  }
}
