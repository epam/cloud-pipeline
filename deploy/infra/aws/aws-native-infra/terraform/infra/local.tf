locals {

  tags = merge({
    Environment = var.env
    Project     = var.project_name
    },
    var.additional_tags
  )

  resource_name_prefix = "${var.project_name}-${var.env}"
  cluster_name         = "${local.resource_name_prefix}-eks-cluster"
  efs_name             = "${local.resource_name_prefix}-efs-file-system"

  sso_additional_role_mapping = [
    for role_mapping in var.eks_additional_role_mapping : {
      rolearn  = role_mapping.iam_role_arn
      username = role_mapping.eks_role_name
      groups   = role_mapping.eks_groups
    }
  ]

}
