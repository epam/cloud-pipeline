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
  description = "The role of the cluster node for nodes from Cloud-Pipeline system node group."
  value       = aws_iam_role.eks_cp_system_node_execution
}

output "cluster_cp_worker_node_execution_role" {
  description = "The role of the cluster node, for Cloud-Pipeline worker nodes which will be launched by Cloud-Pipeline."
  value       = aws_iam_role.eks_cp_worker_node_execution
}

output "cp_s3_via_sts_role" {
  description = "Role with permissions for Cloud-Pipeline S3iaSTS"
  value       = aws_iam_role.cp_s3_via_sts.arn
}

output "cluster_cp_main_execution_role" {
  description = "Permissions for Cloud-Pipeline in cluster"
  value       = module.cp_irsa.iam_role_arn
}

output "cp_ssh_rsa_key_pair" {
  description = "RSA key pair to use during Cloud-Pipeline deployment"
  value       = module.ssh_rsa_key_pair
}

output "cp_etc_bucket" {
  description = "Cloud-pipeline etc bucket name"
  value       = module.s3_etc.s3_bucket_id
}

output "cp_docker_bucket" {
  description = "Cloud-pipeline docker registry bucket name"
  value       = module.s3_docker.s3_bucket_id
}

output "rds_root_pass_secret" {
  description = "Id of the secretsmanager secret where password of the RDS root_user is stored"
  value       = try(aws_secretsmanager_secret.rds_root_secret[0].id, null)
}

output "rds_address" {
  description = "The address of the RDS instance"
  value       = try(module.cp_rds.db_instance_address, null)
}

output "rds_root_username" {
  description = "Username of the RDS default user"
  value       = try(module.cp_rds.db_instance_username, null)
}

output "rds_port" {
  description = "The port on which the RDS instance accepts connections"
  value       = try(module.cp_rds.db_instance_port, null)
}

output "cp_efs_filesystem_id" {
  description = "ID of the created efs filesystem"
  value       = try(module.cp_system_efs.id, null)
}

output "cp_efs_filesystem_exec_role" {
  description = "Execution role with permission to interact with efs filesystem, used by SCI driver"
  value       = try(module.efs_csi_irsa.iam_role_arn, null)
}

output "cp_fsx_filesystem_id" {
  description = "ID of the created fsx filesystem"
  value       = try(aws_fsx_lustre_file_system.fsx[0].id, null)
}

output "cp_fsx_filesystem_exec_role" {
  description = "Execution role with permission to interact with fsx filesystem, used by SCI driver"
  value       = try(module.fsx_csi_irsa.iam_role_arn, null)
}

output "cp_kms_arn" {
  description = "ARN of created KMS key.This kms is used to encrypt Cloud Pipeline system buckets and efs"
  value       = try(module.kms.key_arn, null)
}

output "eks_cluster_primary_security_group_id" {
  description = "Primary security group that used by cluster"
  value       = try(module.internal_cluster_access_sg.security_group_id, null)
}

output "https_access_security_group" {
  description = "Security group that used by load balancer with https public access"
  value       = try(module.https_access_sg.security_group_id, null)
}

