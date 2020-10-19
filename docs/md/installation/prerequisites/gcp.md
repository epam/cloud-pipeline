# GCP 

**Note**: Account specific information shall be updated according to the environment:

* Account name: `<account-name>`
* Project ID: `<project-id>`
* Region: `<region-id>`

## VPC

A new VPC in the `<project-id>` project and `<region-id>` region with the default configuration

## Subnets

* A routable subnet with /26 CIDR or a subnet with the Elastic IPs allowed. It will be used to deploy user-facing services
* Non routable subnets with any CIDR range (e.g. /16) in each of the available Availability Zones. These subnets will be used to launch the worker nodes. E.g. for us-east-1 region, the following subnets/CIDRs can be created:
  * us-east-1a: 10.0.0.0/23
  * us-east-1b: 10.0.2.0/23
  * us-east-1c: 10.0.4.0/23
  * us-east-1d: 10.0.6.0/23
  * us-east-1e: 10.0.8.0/23
  * us-east-1f: 10.0.10.0/23

## Security Groups

* CP-Cluster-Internal:
  * Traffic type: ALL
  * Ports: ALL
  * Inbound: from <CP-Cluster-Internal>
  * Outbound: to <CP-Cluster-Internal>

* CP-HTTPS-Access:
  * Traffic type: HTTPS
  * Port: 443
  * Inbound: from `Internal networks or 0.0.0.0 (for the Elastic IPs usage)`

* CP-Internet-Access:
  * Traffic type: Any
  * Port: 3128
  * Outbound: to `Egress HTTP proxy, if applicable`

## AMI

The following AMIs shall be white-listed for the AWS Account:

* CPU-only: `ami-0383ffd4e3eea1784`
* GPU/CUDA: `ami-0b1141f33ec5cf631`

## VPC S3 Endpoint

A new VPC endpoint shall be created for the S3 service. No specific configuration is needed

## SSH Key

A new SSH key named `CP-SSH-Key`

## IAM

### Policies

* Name: **CP-Service-Policy**

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "S3Allow",
            "Effect": "Allow",
            "Action": [
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
            "Resource": [
                "arn:aws:s3:::*"
            ]
        },
        {
            "Sid": "EFSAllow",
            "Effect": "Allow",
            "Action": [
                "elasticfilesystem:*"
            ],
            "Resource": [
                "arn:aws:elasticfilesystem:<region-id>:*:file-system/*"
            ]
        },
        {
            "Sid": "OtherAllow",
            "Effect": "Allow",
            "Action": [
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
                "cloudwatch:GetMetricData"
            ],
            "Resource": "*"
        },
        {
            "Sid": "STSAllow",
            "Effect": "Allow",
            "Action": [
                "sts:AssumeRole"
            ],
            "Resource": [
                "arn:aws:iam::<account-id>:role/CP-S3viaSTS"
            ]
        }
    ]
}
```

* Name: **CP-KMS-Assume-Policy**

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "KMSAllowOps",
            "Effect": "Allow",
            "Action": [
                "kms:Decrypt",
                "kms:Encrypt",
                "kms:DescribeKey",
                "kms:ReEncrypt*",
                "kms:GenerateDataKey*"
                "kms:ListKeys"
            ],
            "Resource": "*"
        }
    ]
}
```

* Name: **CP-S3viaSTS-Policy**

```
{
    "Version": "2012-10-17",
    "Statement": [
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
                "s3:PutBucketTagging"
            ],
            "Resource": [
                "arn:aws:s3:::*"
            ]
        }
    ]
}
```

### Roles

* **AWSServiceRoleForEC2Spot**: policies according to the AWS Documentation [Manually create the AWSServiceRoleForEC2Spot service-linked role](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/spot-requests.html#service-linked-roles-spot-instance-requests)

* **CP-Service**: CP-Service-Policy and CP-KMS-Assume-Policy

* **CP-S3viaSTS**: 
  * Policies: CP-S3viaSTS-Policy and CP-KMS-Assume-Policy
  * Trust relationship:
```
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::<account-id>:role/CP-Service"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

## KMS

The following AWS KMS key shall be created:
* Region: `<region-id>`
* Name: CP-KMS-`<region-id>`
* Description: Cloud Pipeline KMS encryption key
* Key Material: `AWS_KMS`

The following policy shall be attached to the key:

```
{
    "Id": "CP-KMS-Key-Policy",
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "Enable IAM User Permissions",
            "Effect": "Allow",
            "Principal": {
                "AWS": "arn:aws:iam::<account-id>:root"
            },
            "Action": "kms:*",
            "Resource": "*"
        },
        {
            "Sid": "Allow access for Key Administrators",
            "Effect": "Allow",
            "Principal": {
                "AWS": [
                    "arn:aws:iam::<account-id>:role/CP-Service",
                    "arn:aws:iam::<account-id>:role/aws-service-role/spot.amazonaws.com/AWSServiceRoleForEC2Spot"
                ]
            },
            "Action": [
                "kms:Create*",
                "kms:Describe*",
                "kms:Enable*",
                "kms:List*",
                "kms:Put*",
                "kms:Update*",
                "kms:Revoke*",
                "kms:Disable*",
                "kms:Get*",
                "kms:Delete*",
                "kms:TagResource",
                "kms:UntagResource",
                "kms:ScheduleKeyDeletion",
                "kms:CancelKeyDeletion"
            ],
            "Resource": "*"
        },
        {
            "Sid": "Allow use of the key",
            "Effect": "Allow",
            "Principal": {
                "AWS": [
                    "arn:aws:iam::<account-id>:role/CP-Service",
                    "arn:aws:iam::<account-id>:role/aws-service-role/spot.amazonaws.com/AWSServiceRoleForEC2Spot"
                ]
            },
            "Action": [
                "kms:Encrypt",
                "kms:Decrypt",
                "kms:ReEncrypt*",
                "kms:GenerateDataKey*",
                "kms:DescribeKey"
            ],
            "Resource": "*"
        },
        {
            "Sid": "Allow attachment of persistent resources",
            "Effect": "Allow",
            "Principal": {
                "AWS": [
                    "arn:aws:iam::<account-id>:role/CP-Service",
                    "arn:aws:iam::<account-id>:role/aws-service-role/spot.amazonaws.com/AWSServiceRoleForEC2Spot"
                ]
            },
            "Action": [
                "kms:CreateGrant",
                "kms:ListGrants",
                "kms:RevokeGrant"
            ],
            "Resource": "*",
            "Condition": {
                "Bool": {
                    "kms:GrantIsForAWSResource": "true"
                }
            }
        }
    ]
}
```

