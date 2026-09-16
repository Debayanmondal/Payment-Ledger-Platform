output "vpc_id" {
  value       = aws_vpc.main.id
  description = "The ID of the provisioned VPC"
}

output "rds_endpoint" {
  value       = aws_db_instance.postgres.endpoint
  description = "Connection endpoint for Amazon RDS PostgreSQL"
}

output "redis_endpoint" {
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
  description = "Connection endpoint for Amazon ElastiCache Redis"
}

output "eks_cluster_name" {
  value       = aws_eks_cluster.platform_cluster.name
  description = "The name of the Amazon EKS cluster"
}
