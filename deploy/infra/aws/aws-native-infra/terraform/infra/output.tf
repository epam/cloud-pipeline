output "cluster_id" {
  description = "The ID of the created EKS cluster."
  value       = module.eks.cluster_id
}

output "cluster_name" {
  description = "The name of the created EKS cluster."
  value       = module.eks.cluster_name
}

output "cluster_arn" {
  description = "The ARN of the created EKS cluster."
  value       = module.eks.cluster_arn
}

output "cluster_endpoint" {
  description = "The endpoint of the created EKS cluster."
  value       = module.eks.cluster_endpoint
}

output "cluster_certificate_authority_data" {
  description = "The Certificate Authority of the created EKS cluster"
  value       = module.eks.cluster_certificate_authority_data
}

output "cluster_node_execution_role" {
  description = "The role of the cluster node execution."
  value       = aws_iam_role.eks_node_execution
}

output "etc_bucket" {
  description = "Cloud-pipeline etc bucket name"
  value = module.s3_etc.s3_bucket_id
}

output "docker_bucket" {
  description = "Cloud-pipeline docker registry bucket name"
  value = module.s3_docker.s3_bucket_id
}
