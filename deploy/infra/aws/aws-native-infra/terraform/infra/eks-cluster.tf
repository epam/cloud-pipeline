/*
===============================================================================
  AWS EKS cluster
===============================================================================
*/
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "19.5.1"

  cluster_name    = local.cluster_name
  cluster_version = var.eks_cluster_version
  create_iam_role = false
  iam_role_arn    = aws_iam_role.eks_cluster_execution.arn

  vpc_id                         = data.aws_vpc.this.id
  subnet_ids                     = data.aws_subnets.this.ids
  cluster_endpoint_public_access = false

  cluster_additional_security_group_ids = [module.internal_cluster_access_sg.security_group_id]

  # External encryption key
  create_kms_key            = false
  cluster_encryption_config = {
    resources        = ["secrets"]
    provider_key_arn = module.kms_eks.key_arn
  }

  eks_managed_node_group_defaults = {
    ami_type               = "AL2_x86_64"
    create_iam_role        = false
    iam_role_arn           = aws_iam_role.eks_node_execution.arn
    vpc_security_group_ids = [module.internal_cluster_access_sg.security_group_id]
  }

  eks_managed_node_groups = {
    system = {
      name = "${local.resource_name_prefix}-system-ng"

      instance_types = [var.eks_system_node_group_instance_type]
      block_device_mappings = {
        xvda = {
          device_name = "/dev/xvda"
          ebs = {
            volume_size           = var.eks_system_node_group_volume_size
            volume_type           = var.eks_system_node_group_volume_type
            encrypted             = true
            delete_on_termination = true
          }
        }
      }

      labels = {
        "cloud-pipeline/node-group-type" : "system"
      }

      min_size     = var.eks_system_node_group_size
      max_size     = var.eks_system_node_group_size
      desired_size = var.eks_system_node_group_size
    }
  }

  # aws-auth configmap
  manage_aws_auth_configmap = true

  aws_auth_roles = [
    {
      rolearn  = aws_iam_role.eks_node_execution.arn
      username = "system:node:{{EC2PrivateDNSName}}"
      groups   = [
        "system:bootstrappers",
        "system:nodes",
      ]
    }
  ]

  cluster_enabled_log_types              = ["audit", "api", "authenticator", "scheduler", "controllerManager"]
  cloudwatch_log_group_retention_in_days = var.eks_cloudwatch_logs_retention_in_days
  cloudwatch_log_group_kms_key_id        = module.kms_eks.key_arn
  tags                                   = local.tags
}

##############################################################
#       Amazon CloudWatch Observability EKS add-on
##############################################################
resource "aws_eks_addon" "cw_observability" {
  addon_name   = "amazon-cloudwatch-observability"
  cluster_name = module.eks.cluster_name

  depends_on = [
    aws_cloudwatch_log_group.cw_application, aws_cloudwatch_log_group.cw_dataplane, aws_cloudwatch_log_group.cw_host,
    aws_cloudwatch_log_group.cw_performance
  ]
}

