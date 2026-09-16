variable "aws_region" {
  type        = string
  default     = "us-east-1"
  description = "AWS deployment region"
}

variable "environment" {
  type        = string
  default     = "prod"
  description = "Deployment environment name"
}

variable "vpc_cidr" {
  type        = string
  default     = "10.0.0.0/16"
  description = "CIDR block for VPC"
}

variable "db_password" {
  type        = string
  sensitive   = true
  default     = "SecureBankingPass123!"
  description = "Master password for Amazon RDS PostgreSQL"
}

variable "eks_role_arn" {
  type        = string
  default     = "arn:aws:iam::123456789012:role/EKSClusterRole"
  description = "IAM Role ARN for Amazon EKS control plane"
}
