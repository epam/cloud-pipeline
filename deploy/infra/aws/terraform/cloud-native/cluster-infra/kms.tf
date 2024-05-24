# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

module "kms" {
  source  = "terraform-aws-modules/kms/aws"
  version = "1.5"

  aliases               = ["${var.deployment_name}/${var.deployment_env}"]
  description           = "${var.deployment_name}/${var.deployment_env} encryption key"
  enable_default_policy = true

  policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Sid" : "Default",
        "Effect" : "Allow",
        "Principal" : {
          "AWS" : "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        },
        "Action" : "kms:*",
        "Resource" : "*"
      },
      {
        "Sid" : "Allow use of the key",
        "Effect" : "Allow",
        "Principal" : {
          "AWS" : [
            "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/aws-service-role/spot.amazonaws.com/AWSServiceRoleForEC2Spot",
            module.cp_irsa.iam_role_arn,
            aws_iam_role.eks_cp_system_node_execution.arn,
            aws_iam_role.eks_cp_worker_node_execution.arn
          ],
          "Service": "omics.amazonaws.com"
        },
        "Action" : [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ],
        "Resource" : "*"
      },
      {
        "Sid" : "Allow attachment of persistent resources",
        "Effect" : "Allow",
        "Principal" : {
          "AWS" : [
            "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/aws-service-role/spot.amazonaws.com/AWSServiceRoleForEC2Spot",
            module.cp_irsa.iam_role_arn,
            aws_iam_role.eks_cp_system_node_execution.arn
          ]
        },
        "Action" : [
          "kms:CreateGrant",
          "kms:ListGrants",
          "kms:RevokeGrant"
        ],
        "Resource" : "*",
        "Condition" : {
          "Bool" : {
            "kms:GrantIsForAWSResource" : "true"
          }
        }
      }
    ]
  })

  tags = local.tags
}

module "kms_eks" {
  source  = "terraform-aws-modules/kms/aws"
  version = "1.5"

  aliases               = ["${var.deployment_name}/${var.deployment_env}/eks"]
  description           = "${var.deployment_name}/${var.deployment_env} encryption key for eks cluster"
  enable_default_policy = true

  policy = jsonencode({
    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Sid" : "Default",
        "Effect" : "Allow",
        "Principal" : {
          "AWS" : "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        },
        "Action" : "kms:*",
        "Resource" : "*"
      },
      {
        "Sid" : "Allow logs encryption",
        "Effect" : "Allow",
        "Principal" : {
          "Service" : "logs.${data.aws_region.current.name}.amazonaws.com"
        },
        "Action" : [
          "kms:Encrypt*",
          "kms:Decrypt*",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:Describe*"
        ],
        "Resource" : "*",
        "Condition" : {
          "ArnLike" : {
            "kms:EncryptionContext:aws:logs:arn" : "arn:aws:logs:*:${data.aws_caller_identity.current.account_id}:*"
          }
        }
      },
      {
        "Sid" : "Allow logs encryption",
        "Effect" : "Allow",
        "Principal" : {
          "AWS" : [
            aws_iam_role.eks_cluster_execution.arn, aws_iam_role.eks_cp_system_node_execution.arn,
            aws_iam_role.eks_cp_worker_node_execution.arn
          ]
        },
        "Action" : [
          "kms:Encrypt*",
          "kms:Decrypt*",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:Describe*"
        ],
        "Resource" : "*"
      }
    ]
  })

  tags = local.tags
}

