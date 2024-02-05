/*
===============================================================================
  EKS Cluster IAM
===============================================================================
*/
resource "aws_iam_role" "eks_cluster_execution" {
  name = "${local.resource_name_prefix}EKSClusterExecutionRole"

  permissions_boundary = var.iam_role_permissions_boundary_arn

  assume_role_policy = jsonencode({

    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Principal" : {
          "Service" : [
            "eks.amazonaws.com"
          ]
        },
        "Action" : "sts:AssumeRole"
      }
    ]

  })
}

data "aws_iam_policy" "AmazonEKSClusterPolicy" {
  arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}


resource "aws_iam_role_policy_attachment" "eks_cluster" {
  role       = aws_iam_role.eks_cluster_execution.name
  policy_arn = data.aws_iam_policy.AmazonEKSClusterPolicy.arn
}


/*
===============================================================================
  EKS Node IAM
===============================================================================
*/
resource "aws_iam_role" "eks_node_execution" {
  name = "${local.resource_name_prefix}EKSNodeExecutionRole"

  permissions_boundary = var.iam_role_permissions_boundary_arn

  assume_role_policy = jsonencode({

    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Principal" : {
          "Service" : [
            "ec2.amazonaws.com"
          ]
        },
        "Action" : "sts:AssumeRole"
      }
    ]

  })
}

resource "aws_iam_policy" "eks_node_observability" {
  name        = "${local.resource_name_prefix}_eks_observability"
  description = "Access to write logs by CW Agent and FluentBit to the cloudwatch log groups"
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Effect" : "Allow",
        "Action" : [
          "cloudwatch:PutMetricData",
          "ec2:DescribeVolumes",
          "ec2:DescribeTags",
          "logs:PutLogEvents",
          "logs:PutRetentionPolicy",
          "logs:DescribeLogStreams",
          "logs:DescribeLogGroups",
          "logs:CreateLogStream",
          "logs:CreateLogGroup"
        ],
        "Resource" : "*"
      },
      {
        "Effect" : "Allow",
        "Action" : [
          "ssm:GetParameter"
        ],
        "Resource" : "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/AmazonCloudWatch-*"
      },
      {
        "Effect" : "Allow",
        "Action" : [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:GenerateDataKey",
        ],
        "Resource" : module.kms.key_arn
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "eks_node_observability" {
  role       = aws_iam_role.eks_node_execution.name
  policy_arn = aws_iam_policy.eks_node_observability.arn
}

data "aws_iam_policy" "AmazonEKSWorkerNodePolicy" {
  arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}


resource "aws_iam_role_policy_attachment" "eks_worker_node" {
  role       = aws_iam_role.eks_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonEKSWorkerNodePolicy.arn
}

data "aws_iam_policy" "AmazonEC2ContainerRegistryReadOnly" {
  arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "eks_node_ecr_read_only" {
  role       = aws_iam_role.eks_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonEC2ContainerRegistryReadOnly.arn
}

data "aws_iam_policy" "AmazonEKS_CNI_Policy" {
  arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "eks_node_cni" {
  role       = aws_iam_role.eks_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonEKS_CNI_Policy.arn
}

data "aws_iam_policy" "AWSXrayWriteOnlyAccess" {
  arn = "arn:aws:iam::aws:policy/AWSXrayWriteOnlyAccess"
}

resource "aws_iam_role_policy_attachment" "AWSXrayWriteOnly" {
  role       = aws_iam_role.eks_node_execution.name
  policy_arn = data.aws_iam_policy.AWSXrayWriteOnlyAccess.arn
}

data "aws_iam_policy" "AmazonFSxFullAccess" {
  arn = "arn:aws:iam::aws:policy/AmazonFSxFullAccess"
}


data "aws_iam_policy" "AmazonElasticFileSystemFullAccess" {
  arn = "arn:aws:iam::aws:policy/AmazonElasticFileSystemFullAccess"
}

module "fsx_csi_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "5.30.0"

  role_name                     = "${local.resource_name_prefix}-fsx_csi-ExecutionRole"
  role_permissions_boundary_arn = var.iam_role_permissions_boundary_arn
  policy_name_prefix            = local.resource_name_prefix

  attach_fsx_lustre_csi_policy = true
  role_policy_arns             = {
    fsx_policy = data.aws_iam_policy.AmazonFSxFullAccess.arn
  }

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:fsx-csi-controller-sa"]
    }
  }

  tags = local.tags
}

module "efs_csi_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "5.30.0"

  role_name                     = "${local.resource_name_prefix}-efs_csi-ExecutionRole"
  role_permissions_boundary_arn = var.iam_role_permissions_boundary_arn
  policy_name_prefix            = local.resource_name_prefix

  attach_efs_csi_policy = true
  role_policy_arns      = {
    efs_policy = data.aws_iam_policy.AmazonElasticFileSystemFullAccess.arn
  }

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:efs-csi-controller-sa"]
    }
  }

  tags = local.tags
}

/*
===============================================================================
  Cloud-Pipeline Service main role
===============================================================================
*/
resource "aws_iam_policy" "cp_main_service" {
  name        = "${local.resource_name_prefix}ServicePolicy"
  description = "Permissions for Cloud-Pipeline in ${local.resource_name_prefix}"
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Sid" : "S3Allow",
        "Effect" : "Allow",
        "Action" : [
          "s3:GetLifecycleConfiguration",
          "s3:GetBucketTagging",
          "s3:DeleteObjectVersion",
          "s3:GetObjectVersionTagging",
          "s3:ListBucketVersions",
          "s3:RestoreObject",
          "s3:CreateBucket",
          "s3:ListBucket",
          "s3:GetBucketPolicy",
          "s3:GetObjectAcl",
          "s3:AbortMultipartUpload",
          "s3:PutBucketTagging",
          "s3:PutLifecycleConfiguration",
          "s3:PutBucketAcl",
          "s3:GetObjectTagging",
          "s3:PutObjectTagging",
          "s3:*Delete*",
          "s3:PutBucketVersioning",
          "s3:PutObjectAcl",
          "s3:DeleteObjectTagging",
          "s3:ListBucketMultipartUploads",
          "s3:PutObjectVersionTagging",
          "s3:DeleteObjectVersionTagging",
          "s3:GetBucketVersioning",
          "s3:PutBucketCORS",
          "s3:GetBucketAcl",
          "s3:ListMultipartUploadParts",
          "s3:PutObject",
          "s3:GetObject",
          "s3:GetBucketCORS",
          "s3:PutBucketPolicy",
          "s3:GetBucketLocation",
          "s3:GetObjectVersion",
          "s3:PutEncryptionConfiguration",
          "s3:GetEncryptionConfiguration",
          "s3:ListAllMyBuckets"
        ],
        "Resource" : [
          "arn:aws:s3:::*"
        ]
      },
      {
        "Sid" : "EFSAllow",
        "Effect" : "Allow",
        "Action" : [
          "elasticfilesystem:*"
        ],
        "Resource" : [
          "arn:aws:elasticfilesystem:${data.aws_region.current.id}:*:file-system/*"
        ]
      },
      {
        "Sid" : "FSxAllowListing",
        "Effect" : "Allow",
        "Action" : [
          "fsx:DescribeFileSystems",
          "fsx:DescribeBackups",
          "fsx:DescribeDataRepositoryTasks"
        ],
        "Resource" : "*"
      },
      {
        "Sid" : "FSxAllowRW",
        "Effect" : "Allow",
        "Action" : "fsx:*",
        "Resource" : [
          "arn:aws:fsx:${data.aws_region.current.id}::task/*",
          "arn:aws:fsx:${data.aws_region.current.id}::backup/*",
          "arn:aws:fsx:${data.aws_region.current.id}::file-system/*"
        ]
      },
      {
        "Sid" : "FSxAllowFullControl",
        "Effect" : "Allow",
        "Action" : [
          "fsx:*"
        ],
        "Resource" : "*"
      },
      {
        "Sid" : "OtherAllow",
        "Effect" : "Allow",
        "Action" : [
          "ec2:AttachVolume",
          "ec2:DeregisterImage",
          "kms:Decrypt",
          "ec2:DeleteSnapshot",
          "ec2:RequestSpotInstances",
          "ec2:DeleteTags",
          "elasticfilesystem:CreateFileSystem",
          "ec2:CancelSpotFleetRequests",
          "ec2:CreateKeyPair",
          "ec2:CreateImage",
          "ec2:RequestSpotFleet",
          "ec2:DeleteVolume",
          "ec2:DescribeNetworkInterfaces",
          "ec2:StartInstances",
          "kms:Encrypt",
          "ec2:CreateSnapshot",
          "kms:ReEncryptTo",
          "kms:DescribeKey",
          "ec2:ModifyInstanceAttribute",
          "ec2:DescribeInstanceStatus",
          "ec2:DetachVolume",
          "ec2:ReleaseAddress",
          "ec2:RebootInstances",
          "iam:GetRole",
          "ec2:TerminateInstances",
          "ec2:CreateTags",
          "ec2:RegisterImage",
          "iam:ListRoles",
          "ec2:RunInstances",
          "ec2:StopInstances",
          "kms:ReEncryptFrom",
          "ec2:CreateVolume",
          "ec2:CreateNetworkInterface",
          "ec2:CancelSpotInstanceRequests",
          "ec2:Describe*",
          "kms:GenerateDataKey",
          "ec2:DescribeSubnets",
          "ec2:DeleteKeyPair",
          "cloudwatch:GetMetricStatistics",
          "cloudwatch:ListMetrics",
          "cloudwatch:PutMetricData",
          "cloudwatch:GetMetricData",
          "savingsplans:DescribeSavingsPlans",
          "savingsplans:DescribeSavingsPlanRates"
        ],
        "Resource" : "*"
      },
      {
        "Sid" : "STSAllow",
        "Effect" : "Allow",
        "Action" : [
          "sts:AssumeRole"
        ],
        "Resource" : [
          "arn:aws:iam::*:role/${local.resource_name_prefix}*"
        ]
      }
    ]
  })
  tags = local.tags
}

module "cp_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "5.30.0"

  role_name                     = "${local.resource_name_prefix}CPExecutionRole"
  role_permissions_boundary_arn = var.iam_role_permissions_boundary_arn
  policy_name_prefix            = local.resource_name_prefix

  attach_fsx_lustre_csi_policy = true
  role_policy_arns             = {
    policy = aws_iam_policy.cp_main_service.arn
  }

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["default:cp-api-srv"]
    }
  }

  tags = local.tags
}

/*
===============================================================================
  Cloud-Pipeline S3viaSTS role
===============================================================================
*/
resource "aws_iam_policy" "cp_s3_via_sts" {
  name        = "${local.resource_name_prefix}S3viaSTSPolicy"
  description = "Permissions for Cloud-Pipeline S3iaSTS Role in ${local.resource_name_prefix}"
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Effect": "Allow",
        "Action": [
          "s3:ListBucket",
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:GetObjectTagging",
          "s3:PutObjectTagging",
          "s3:DeleteObjectTagging",
          "s3:GetObjectVersionTagging",
          "s3:PutObjectVersionTagging",
          "s3:DeleteObjectVersionTagging",
          "s3:GetObjectVersion",
          "s3:DeleteObjectVersion",
          "s3:ListBucketVersions",
          "s3:GetBucketTagging",
          "s3:PutBucketTagging",
          "s3:PutObjectAcl",
          "s3:GetObjectAcl"
        ],
        "Resource": [
          "arn:aws:s3:::*"
        ]
      },
      {
        "Sid": "KMSAllowOps",
        "Effect": "Allow",
        "Action": [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:DescribeKey",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:ListKeys"
        ],
        "Resource": "*"
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_role" "cp_s3_via_sts" {
  name = "${local.resource_name_prefix}S3viaSTSRole"

  permissions_boundary = var.iam_role_permissions_boundary_arn

  assume_role_policy = jsonencode({

    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Principal" : {
          "AWS" : module.cp_irsa.iam_role_arn
        },
        "Action" : "sts:AssumeRole"
      }
    ]

  })
}

resource "aws_iam_role_policy_attachment" "cp_s3_via_sts" {
  role       = aws_iam_role.cp_s3_via_sts.name
  policy_arn = aws_iam_policy.cp_s3_via_sts.arn
}