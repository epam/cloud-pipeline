resource "aws_cloudwatch_log_group" "cw_performance" {
  name              = "/aws/containerinsights/${module.eks.cluster_name}/performance"
  kms_key_id        = module.kms_eks.key_arn
  retention_in_days = var.eks_cloudwatch_logs_retention_in_days
}

resource "aws_cloudwatch_log_group" "cw_application" {
  name              = "/aws/containerinsights/${module.eks.cluster_name}/application"
  kms_key_id        = module.kms_eks.key_arn
  retention_in_days = var.eks_cloudwatch_logs_retention_in_days
}

resource "aws_cloudwatch_log_group" "cw_dataplane" {
  name              = "/aws/containerinsights/${module.eks.cluster_name}/dataplane"
  kms_key_id        = module.kms.key_arn
  retention_in_days = var.eks_cloudwatch_logs_retention_in_days
}

resource "aws_cloudwatch_log_group" "cw_host" {
  name              = "/aws/containerinsights/${module.eks.cluster_name}/host"
  kms_key_id        = module.kms_eks.key_arn
  retention_in_days = var.eks_cloudwatch_logs_retention_in_days
}
