locals {

  tags = merge({
    Environment = var.env
    Project     = var.project_name
  },
    var.additional_tags
  )

  eks_system_node_labels = {
    "cloud-pipeline/node-group-type" : "system"
  }

  resource_name_prefix = "${var.project_name}-${var.env}"
  cluster_name         = "${local.resource_name_prefix}-eks-cluster"
  efs_name             = "${local.resource_name_prefix}-efs-file-system"

  system_node_groups = {
    for subnet_id in var.eks_system_node_group_subnet_ids : "system_${subnet_id}" => {
      name = "system-${subnet_id}-ng"

      subnet_ids            = [subnet_id]
      instance_types        = [var.eks_system_node_group_instance_type]
      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs         = {
            volume_size           = var.eks_system_node_group_volume_size
            volume_type           = var.eks_system_node_group_volume_type
            encrypted             = true
            delete_on_termination = true
          }
        }
      }

      labels = merge(
        local.eks_system_node_labels,
        {
          "cloud-pipeline/node-group-subnet" : subnet_id
        }
      )

      min_size     = var.eks_system_node_group_size
      max_size     = var.eks_system_node_group_size
      desired_size = var.eks_system_node_group_size
    }
  }

  sso_additional_role_mapping = [
    for role_mapping in var.eks_additional_role_mapping : {
      rolearn  = role_mapping.iam_role_arn
      username = role_mapping.eks_role_name
      groups   = role_mapping.eks_groups
    }
  ]

}
