/*
===============================================================================
  Common IAM policies
===============================================================================
*/
data "aws_iam_policy" "AmazonEKSClusterPolicy" {
  arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

data "aws_iam_policy" "AmazonSSMManagedInstanceCore" {
  arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy" "AmazonEC2ContainerRegistryReadOnly" {
  arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

data "aws_iam_policy" "AmazonEKSWorkerNodePolicy" {
  arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

data "aws_iam_policy" "AmazonEKS_CNI_Policy" {
  arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

data "aws_iam_policy" "AWSXrayWriteOnlyAccess" {
  arn = "arn:aws:iam::aws:policy/AWSXrayWriteOnlyAccess"
}

data "aws_iam_policy" "AmazonFSxFullAccess" {
  arn = "arn:aws:iam::aws:policy/AmazonFSxFullAccess"
}

data "aws_iam_policy" "AmazonElasticFileSystemFullAccess" {
  arn = "arn:aws:iam::aws:policy/AmazonElasticFileSystemFullAccess"
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
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_policy" "kms_eks_access" {
  name        = "${local.resource_name_prefix}_kms_eks_access"
  description = "Access to KMS for EKS"
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Effect" : "Allow",
        "Action" : [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:DescribeKey",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:ListKeys"
        ],
        "Resource" : module.kms_eks.key_arn
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_policy" "kms_data_access" {
  name        = "${local.resource_name_prefix}_kms_access"
  description = "Access to KMS for Data"
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Effect" : "Allow",
        "Action" : [
          "kms:Decrypt",
          "kms:Encrypt",
          "kms:DescribeKey",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:ListKeys"
        ],
        "Resource" : module.kms.key_arn
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_policy" "cp_ecr_access" {
  name        = "${local.resource_name_prefix}ECROmicsAccessPolicy"
  description = "Permissions for access ECR for Omics in ${local.resource_name_prefix}"
  count       = var.enable_aws_omics_integration ? 1 : 0
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Effect": "Allow",
        "Action": [
          "ecr:DescribeRegistry",
          "ecr:DescribePullThroughCacheRules",
          "ecr:DescribeRepositoryCreationTemplate",
          "ecr:GetRegistryPolicy",
          "ecr:GetAuthorizationToken",
          "ecr:GetRegistryScanningConfiguration",
          "ecr:ValidatePullThroughCacheRule",
          "ecr:CreateRepository",
          "ecr:CreateRepositoryCreationTemplate",
          "ecr:PutRegistryPolicy",
          "ecr:PutReplicationConfiguration",
          "ecr:PutRegistryScanningConfiguration",
          "ecr:BatchImportUpstreamImage",
          "ecr:UpdatePullThroughCacheRule"
        ],
        "Resource": "*"
      },
      {
        "Effect": "Allow",
        "Action": [
          "ecr:DescribeImageReplicationStatus",
          "ecr:DescribeRepositories",
          "ecr:DescribeImageScanFindings",
          "ecr:DescribeImages",
          "ecr:ListImages",
          "ecr:ListTagsForResource",
          "ecr:GetLifecyclePolicy",
          "ecr:GetLifecyclePolicyPreview",
          "ecr:GetDownloadUrlForLayer",
          "ecr:GetRepositoryPolicy",
          "ecr:BatchGetImage",
          "ecr:BatchGetRepositoryScanningConfiguration",
          "ecr:BatchCheckLayerAvailability",
          "ecr:StartImageScan",
          "ecr:StartLifecyclePolicyPreview",
          "ecr:PutImageTagMutability",
          "ecr:PutLifecyclePolicy",
          "ecr:PutImageScanningConfiguration",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:TagResource",
          "ecr:UntagResource",
          "ecr:ReplicateImage",
          "ecr:SetRepositoryPolicy"
        ],
        "Resource": [
          "arn:aws:ecr:*:${data.aws_caller_identity.current.account_id}:repository/*"
        ]
      }
    ]
  })
  tags = local.tags
}

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
          "*"
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
          "ec2:ModifyInstanceMetadataOptions",
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
      },
      {
        "Effect" : "Allow",
        "Action" : [
          "iam:GetRole",
          "iam:PassRole"
        ],
        "Resource" : [
          aws_iam_role.eks_cp_system_node_execution.arn,
          aws_iam_role.eks_cp_worker_node_execution.arn
        ]
      },
      {
        "Effect" : "Allow",
        "Action" : [
          "omics:*"
        ],
        "Resource" : [
          "arn:aws:omics:*:${data.aws_caller_identity.current.account_id}:referenceStore/*",
          "arn:aws:omics:*:${data.aws_caller_identity.current.account_id}:sequenceStore/*"
        ]
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_policy" "cp_omics_service_role_access" {
  name        = "${local.resource_name_prefix}_Omics_Service_Role_Access_policy"
  description = "Permissions for managing AWS Omics Service role for ${local.resource_name_prefix}"
  count       = var.enable_aws_omics_integration ? 1 : 0
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Effect" : "Allow",
        "Action" : [
          "iam:GetRole",
          "iam:PassRole"
        ],
        "Resource" : [
          aws_iam_role.cp_omics_service[0].arn
        ]
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_policy" "cp_s3_via_sts" {
  name        = "${local.resource_name_prefix}S3viaSTSPolicy"
  description = "Permissions for Cloud-Pipeline S3iaSTS Role in ${local.resource_name_prefix}"
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Effect" : "Allow",
        "Action" : [
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
        "Resource" : [
          "*"
        ]
      },
      {
        "Effect" : "Allow",
        "Action" : [
          "omics:*"
        ],
        "Resource" : [
          "arn:aws:omics:*:${data.aws_caller_identity.current.account_id}:referenceStore/*",
          "arn:aws:omics:*:${data.aws_caller_identity.current.account_id}:sequenceStore/*"
        ]
      }
    ]
  })
  tags = local.tags
}

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

resource "aws_iam_role_policy_attachment" "eks_cluster" {
  role       = aws_iam_role.eks_cluster_execution.name
  policy_arn = data.aws_iam_policy.AmazonEKSClusterPolicy.arn
}

/*
===============================================================================
  EKS Node IAMs
===============================================================================
*/
# Allowing for CP System node execution role to access Docker Registry S3 bucket, because registry:2.7.1 doesn't support
# IRSA auth (to get use of service account on kube to assume AWS Role), and updating to registry:3.0.1-alpha (where IRSA is supported)
# is not possible because of problem with registry token auth
resource "aws_iam_policy" "s3_bucket_docker_store_access" {
  name        = "${local.resource_name_prefix}_s3_bucket_docker_store_access"
  description = "Access to s3 bucket where docker registry will store images"
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Sid" : "S3Allow",
        "Effect" : "Allow",
        "Action" : [
          "s3:GetLifecycleConfiguration",
          "s3:DeleteObjectVersion",
          "s3:GetObjectVersionTagging",
          "s3:ListBucketVersions",
          "s3:RestoreObject",
          "s3:GetObjectAcl",
          "s3:AbortMultipartUpload",
          "s3:GetObjectTagging",
          "s3:PutObjectTagging",
          "s3:*Delete*",
          "s3:PutBucketVersioning",
          "s3:PutObjectAcl",
          "s3:DeleteObjectTagging",
          "s3:ListBucketMultipartUploads",
          "s3:PutObjectVersionTagging",
          "s3:DeleteObjectVersionTagging",
          "s3:ListMultipartUploadParts",
          "s3:PutObject",
          "s3:GetObject",
          "s3:GetBucketCORS",
          "s3:PutBucketPolicy",
          "s3:GetBucketLocation",
          "s3:GetObjectVersion"
        ],
        "Resource" : [
          "${module.s3_docker.s3_bucket_arn}/*"
        ]
      },
      {
        "Sid" : "S3BucketAllow",
        "Effect" : "Allow",
        "Action" : [
          "s3:GetLifecycleConfiguration",
          "s3:GetBucketTagging",
          "s3:ListBucketVersions",
          "s3:ListBucket",
          "s3:GetBucketPolicy",
          "s3:AbortMultipartUpload",
          "s3:PutBucketTagging",
          "s3:PutLifecycleConfiguration",
          "s3:PutBucketAcl",
          "s3:*Delete*",
          "s3:PutBucketVersioning",
          "s3:ListBucketMultipartUploads",
          "s3:GetBucketVersioning",
          "s3:PutBucketCORS",
          "s3:GetBucketAcl",
          "s3:ListMultipartUploadParts",
          "s3:GetBucketCORS",
          "s3:PutBucketPolicy",
          "s3:GetBucketLocation",
          "s3:PutEncryptionConfiguration",
          "s3:GetEncryptionConfiguration",
          "s3:ListAllMyBuckets"
        ],
        "Resource" : [
          module.s3_docker.s3_bucket_arn
        ]
      },
      {
        "Sid" : "S3BucketsAllow",
        "Effect" : "Allow",
        "Action" : [
          "s3:ListAllMyBuckets"
        ],
        "Resource" : [
          "arn:aws:s3:::*"
        ]
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "eks_cp_system_node_ssm_core" {
  role       = aws_iam_role.eks_cp_system_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonSSMManagedInstanceCore.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_system_node_observability" {
  role       = aws_iam_role.eks_cp_system_node_execution.name
  policy_arn = aws_iam_policy.eks_node_observability.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_system_kms_eks_access" {
  role       = aws_iam_role.eks_cp_system_node_execution.name
  policy_arn = aws_iam_policy.kms_eks_access.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_system_node_ecr_read_only" {
  role       = aws_iam_role.eks_cp_system_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonEC2ContainerRegistryReadOnly.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_system_node_cni" {
  role       = aws_iam_role.eks_cp_system_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonEKS_CNI_Policy.arn
}

resource "aws_iam_role" "eks_cp_system_node_execution" {
  name = "${local.resource_name_prefix}EKSCPSystemNodeExecutionRole"

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

resource "aws_iam_role_policy_attachment" "eks_cp_system_node_s3_bucket_docker_store_access" {
  role       = aws_iam_role.eks_cp_system_node_execution.name
  policy_arn = aws_iam_policy.s3_bucket_docker_store_access.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_system_node_kms_data_access" {
  role       = aws_iam_role.eks_cp_system_node_execution.name
  policy_arn = aws_iam_policy.kms_data_access.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_system_worker_node_node_policy" {
  role       = aws_iam_role.eks_cp_system_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonEKSWorkerNodePolicy.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_system_node_AWSXrayWriteOnly" {
  role       = aws_iam_role.eks_cp_system_node_execution.name
  policy_arn = data.aws_iam_policy.AWSXrayWriteOnlyAccess.arn
}


resource "aws_iam_instance_profile" "eks_cp_worker_node" {
  name = "${local.resource_name_prefix}EKSCPWorkerNodeExecutionRole_IP"
  role = aws_iam_role.eks_cp_worker_node_execution.name
}

resource "aws_iam_role" "eks_cp_worker_node_execution" {
  name = "${local.resource_name_prefix}EKSCPWorkerNodeExecutionRole"

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

resource "aws_iam_role_policy_attachment" "eks_cp_worker_node_ssm_core" {
  role       = aws_iam_role.eks_cp_worker_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonSSMManagedInstanceCore.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_worker_node_observability" {
  role       = aws_iam_role.eks_cp_worker_node_execution.name
  policy_arn = aws_iam_policy.eks_node_observability.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_worker_node_kms_eks_access" {
  role       = aws_iam_role.eks_cp_worker_node_execution.name
  policy_arn = aws_iam_policy.eks_node_observability.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_worker_worker_node_node_policy" {
  role       = aws_iam_role.eks_cp_worker_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonEKSWorkerNodePolicy.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_worker_node_ecr_read_only" {
  role       = aws_iam_role.eks_cp_worker_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonEC2ContainerRegistryReadOnly.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_worker_node_cni" {
  role       = aws_iam_role.eks_cp_worker_node_execution.name
  policy_arn = data.aws_iam_policy.AmazonEKS_CNI_Policy.arn
}

resource "aws_iam_role_policy_attachment" "eks_cp_worker_node_AWSXrayWriteOnly" {
  role       = aws_iam_role.eks_cp_worker_node_execution.name
  policy_arn = data.aws_iam_policy.AWSXrayWriteOnlyAccess.arn
}


/*
===============================================================================
  EKS CSI Plugin roles
===============================================================================
*/
module "fsx_csi_irsa" {
  create_role = var.deploy_filesystem_type == "fsx" ? true : false
  source      = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version     = "5.30.0"

  role_name                     = "${local.resource_name_prefix}-fsx_csi-ExecutionRole"
  role_permissions_boundary_arn = var.iam_role_permissions_boundary_arn
  policy_name_prefix            = local.resource_name_prefix

  attach_fsx_lustre_csi_policy = true
  role_policy_arns = {
    fsx_policy = data.aws_iam_policy.AmazonFSxFullAccess.arn
    ksm_allow  = aws_iam_policy.kms_data_access.arn
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
  create_role = var.deploy_filesystem_type == "efs" ? true : false
  source      = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version     = "5.30.0"

  role_name                     = "${local.resource_name_prefix}-efs_csi-ExecutionRole"
  role_permissions_boundary_arn = var.iam_role_permissions_boundary_arn
  policy_name_prefix            = local.resource_name_prefix

  attach_efs_csi_policy = true
  role_policy_arns = {
    efs_policy = data.aws_iam_policy.AmazonElasticFileSystemFullAccess.arn
    ksm_allow  = aws_iam_policy.kms_data_access.arn
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

module "cp_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "5.30.0"

  role_name                     = "${local.resource_name_prefix}CPExecutionRole"
  role_permissions_boundary_arn = var.iam_role_permissions_boundary_arn
  policy_name_prefix            = local.resource_name_prefix

  attach_fsx_lustre_csi_policy = true
  role_policy_arns             = merge(
    {
      policy    = aws_iam_policy.cp_main_service.arn
      kms_allow = aws_iam_policy.kms_data_access.arn
    },
      var.enable_aws_omics_integration ? {
      aws_omics = aws_iam_policy.cp_omics_service_role_access[0].arn
    } : {}
  )

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["default:cp-main-service"]
    }
  }

  tags = local.tags
}

/*
===============================================================================
  Cloud-Pipeline S3viaSTS role
===============================================================================
*/
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

resource "aws_iam_role_policy_attachment" "cp_s3_via_sts_kms_allow" {
  role       = aws_iam_role.cp_s3_via_sts.name
  policy_arn = aws_iam_policy.kms_data_access.arn
}

/*
===============================================================================
  Cloud-Pipeline AWS Omics Service role
===============================================================================
*/

resource "aws_iam_role" "cp_omics_service" {
  name  = "${local.resource_name_prefix}OmicsServiceRole"
  count = var.enable_aws_omics_integration ? 1 : 0

  permissions_boundary = var.iam_role_permissions_boundary_arn

  assume_role_policy = jsonencode({

    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Principal" : {
          "Service" : "omics.amazonaws.com",
          "AWS" : aws_iam_role.eks_cp_worker_node_execution.arn
        },
        "Action" : "sts:AssumeRole"
      }
    ]

  })
}

resource "aws_iam_policy" "cp_omics_service" {
  name        = "${local.resource_name_prefix}OmicsServicePolicy"
  description = "Permissions for Cloud-Pipeline Omics Service Role in ${local.resource_name_prefix}"
  count       = var.enable_aws_omics_integration ? 1 : 0
  path        = "/"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [
      {
        "Effect" : "Allow",
        "Action" : [
          "s3:GetObject",
          "s3:ListBucket",
          "s3:PutObject",
          "s3:GetBucketLocation"
        ],
        "Resource" : [
          "*"
        ]
      },
      {
        "Effect" : "Allow",
        "Action" : [
          "logs:DescribeLogStreams",
          "logs:CreateLogStream",
          "logs:PutLogEvents",
          "logs:GetLogEvents"
        ],
        "Resource" : [
          "arn:aws:logs:*:${data.aws_caller_identity.current.account_id}:log-group:/aws/omics/WorkflowLog:log-stream:*"
        ]
      },
      {
        "Effect" : "Allow",
        "Action" : [
          "logs:CreateLogGroup"
        ],
        "Resource" : [
          "arn:aws:logs:*:${data.aws_caller_identity.current.account_id}:log-group:/aws/omics/WorkflowLog:*"
        ]
      },
      {
        "Action" : [
          "omics:*"
        ],
        "Effect" : "Allow",
        "Resource" : [
          "arn:aws:omics:*:${data.aws_caller_identity.current.account_id}:referenceStore/*",
          "arn:aws:omics:*:${data.aws_caller_identity.current.account_id}:sequenceStore/*",
          "arn:aws:omics:*:${data.aws_caller_identity.current.account_id}:workflow/*",
          "arn:aws:omics:*:${data.aws_caller_identity.current.account_id}:run/*"
        ]
      },
      {
        "Action" : [
          "iam:PassRole"
        ],
        "Effect" : "Allow",
        "Resource" : [
          aws_iam_role.cp_omics_service[0].arn
        ]
      }
    ]
  })
  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "cp_omics_service" {
  count      = var.enable_aws_omics_integration ? 1 : 0
  role       = aws_iam_role.cp_omics_service[0].name
  policy_arn = aws_iam_policy.cp_omics_service[0].arn
}

resource "aws_iam_role_policy_attachment" "cp_omics_service_kms_access" {
  count      = var.enable_aws_omics_integration ? 1 : 0
  role       = aws_iam_role.cp_omics_service[0].name
  policy_arn = aws_iam_policy.kms_data_access.arn
}

resource "aws_iam_role_policy_attachment" "cp_omics_service_ecr_access" {
  count      = var.enable_aws_omics_integration ? 1 : 0
  role       = aws_iam_role.cp_omics_service[0].name
  policy_arn = aws_iam_policy.cp_ecr_access[0].arn
}

/*
===============================================================================
  AWS Load Balancer Controller service account and role
===============================================================================
*/

module "aws_lbc_addon_sa_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "5.30.0"

  role_name                     = "${local.resource_name_prefix}-EKS-LB_Controller_Role"
  role_permissions_boundary_arn = var.iam_role_permissions_boundary_arn
  policy_name_prefix            = local.resource_name_prefix

  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }

  tags = local.tags
}

