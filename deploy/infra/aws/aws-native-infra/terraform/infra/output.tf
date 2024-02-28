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

output "cluster_cp_system_node_execution_role" {
  description = "The role of the cluster node for nodes from cp-system node group."
  value       = aws_iam_role.eks_cp_system_node_execution
}

output "cluster_cp_worker_node_execution_role" {
  description = "The role of the cluster node, for cp-worker nodes which will be launched by Cloud-Pipeline."
  value       = aws_iam_role.eks_cp_worker_node_execution
}

output "cp_ssh_rsa_key_pair_id" {
  description = "RSA key pair to use during Cloud-Pipeline deployment"
  value       =  module.ssh_rsa_key_pair
}

output "cp_etc_bucket" {
  description = "Cloud-pipeline etc bucket name"
  value = module.s3_etc.s3_bucket_id
}

output "cp_docker_bucket" {
  description = "Cloud-pipeline docker registry bucket name"
  value = module.s3_docker.s3_bucket_id
}
