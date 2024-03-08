# Cloud-pipeline based on AWS EKS Deployment

## Overview

This document provides a guidance about the deployment infrastructure using of terraform and deploy Cloud-Pipeline on top of that.
The process of the deployment can be performed with the following steps:

- [Cloud-pipeline based on AWS EKS Deployment](#cloud-pipeline-based-on-aws-eks-deployment)
  - [Overview](#overview)
  - [Deployment: Terraform](#deployment-terraform)
    - [Prerequisites](#prerequisites)
    - [Jump-server deployment](#jump-server-deployment)
    - [Output of `jump-server` module](#output-of-jump-server-module)
    - [Cluster-infrastructure deployment](#cluster-infrastructure-deployment)
    - [Output of `cluster-infrastructure` module](#output-of-cluster-infrastructure-module)
    - [Cloud-pipeline deployment](#cloud-pipeline-deployment)

## Deployment: Terraform

To get started with deployment, please make sure that you satisfy requirements below.

### Prerequisites

| Name                                                                      | Version |
| ------------------------------------------------------------------------- | ------- |
| <a name="requirement_terraform"></a> [terraform](#requirement\_terraform) | = 1.5.0 |

1. Manually create S3 Bucket to store remote state of the terraform deployment.
2. Manually create DynamoDB table to store terraform lock records.
   - Table schema:
   ```
      LockID (String) - Partition key
   ```
This instruction supposes availability of the VPC, list of private subnets and some kind of the public access(public subnet, transit gateway, VPN from corporate network etc.) in AWS account where resources will be deployed.

### Jump-server deployment 

To deploy required resources in your environment with terraform, please follow these steps:
1. Create directory named for example `bastion-host` in your environment deployment location and place your Terraform files there.
2. Configure `bastion-host` deployment.
3. From `bastion-host` directory deploy jump-server instance with `terraform init` `terraform apply` commands.

Example of the deployment `Jump-server` files:

main.tf
```hcl
terraform {
  backend "s3" {
    bucket         = "xxxxxxxxxxxx-infra-test"
    key            = "test-eks-jumpbox/terraform.tfstate"
    region         = "eu-west-1"
    encrypt        = true
    dynamodb_table = "xxxxxxxxxxxx-infra-test"
  }
  required_version = "1.5.0"
}

provider "aws" {
  region = "eu-west-1"
}

module jump-server {
    source = "git::https://github.com/epam/cloud-pipeline//deploy/infra/aws/terraform/cloud-native/jump-server?ref=<branch-tag-or-commit>"
    project_name                      = "xxxxxxxxxxxx"
    env                               = "test"
    vpc_id                            = "vpc-xxxxxxxxxxxx"
    subnet_id                         = "subnet-xxxxxxxxxxxx"
    iam_role_permissions_boundary_arn   = "arn:aws:iam::xxxxxxxxxxxx:policy/eo_role_boundary"
}
```
output.tf
```hcl
output "instance_connection" {
  value = module.jump-server.output_message
}

output "instance_id" {
  value = module.jump-server.jump_sever_id
}

output "instance_role" {
  value = module.jump-server.jump_server_role
}
```

There is a list of variables that need to be passed:

| Name                                | Description                                                                                                                          |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `project_name`                      | Name of the deployment. Will be used as resource name prefix of the created resources (security groups, iam roles etc.)              |
| `env`                               | Environment name for the deployment. Will be used as resource name prefix of the created resources (security groups, IAM roles etc.) |
| `vpc_id`                            | Id of the VCP to be used for deployment of the bastion instance.                                                                     |
| `subnet_id`                         | Id of the VCP subnet to be used to launch an instance                                                                                |
| `ami_id`                            | (Optional) AMI to be used for bastion ec2 instance. If empty - eks-optimized will be used.                                           |
| `iam_role_permissions_boundary_arn` | (Optional) Account specific role boundaries                                                                                          |

### Output of `jump-server` module

Terraform module has the following outputs:

| Name               | Description                                                                                                                           |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| `jump_sever_id`    | Id of created Jump Server instance.                                                                                                   |
| `output_message`   | Login to Jump Server with command: aws ssm start-session --target ${module.ec2_instance.id} --region ${data.aws_region.current.name}. |
| `jump_server_role` | ARN of bastion execution role that must be set in EKS deployment module                                                               |

In the file Output.tf change the `jump-server` to your module name or leave as is if module name not changed.


### Cluster-infrastructure deployment 

1. Connect to created jump-server instance. 
2. Create directory named for example `cluster-infrastructure` and place your Terraform files there.
3. Configure `cluster-infrastructure` deployment.
4. From `cluster-infrastructure` directory deploy EKS cluster and infrastructure with `terraform init` `terraform apply` commands.

Example of the deployment `cluster-infrastructure` files:

main.tf
```hcl
terraform {
  backend "s3" {
    bucket         = "xxxxxxxxxxxx-infra-test"
    key            = "test-eks/terraform.tfstate"
    region         = "eu-west-1"
    encrypt        = true
    dynamodb_table = "xxxxxxxxxxxx-infra-test"
  }
  required_version = "1.5.0"
}

provider "aws" {
  region = "eu-west-1"
}

provider "kubernetes" {
  host                   = module.cluster-infra.cluster_endpoint
  cluster_ca_certificate = base64decode(module.cluster-infra.cluster_certificate_authority_data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    # This requires the awscli to be installed locally where Terraform is executed
    args = ["eks", "get-token", "--cluster-name", module.cluster-infra.cluster_name]
  }
}

provider "helm" {
  kubernetes {
    host                   = module.cluster-infra.cluster_endpoint
    cluster_ca_certificate = base64decode(module.cluster-infra.cluster_certificate_authority_data)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      # This requires the awscli to be installed locally where Terraform is executed
      args = ["eks", "get-token", "--cluster-name", module.cluster-infra.cluster_name]
    }
  }
}

provider "postgresql" {
  host      = module.cluster-infra.rds_address
  port      = module.cluster-infra.rds_port
  username  = module.cluster-infra.rds_root_username
  password  = module.cluster-infra.rds_root_pass_secret
  superuser = false
}

module "cluster-infra" {
  source                            = "git::https://github.com/epam/cloud-pipeline//deploy/infra/aws/terraform/cloud-native/cluster-infra?ref=<branch-tag-or-commit>"
  project_name                      = "xxxxxxxxxxxx"
  env                               = "test"
  vpc_id                            = "vpc-xxxxxxxxxxxx"
  cp_api_access_prefix_lists        = ["pl-xxxxxxxxxxxx"]
  subnet_ids                        = ["subnet-xxxxxxxxxxxx", "subnet-xxxxxxxxxxxx", "subnet-xxxxxxxxxxxx"]
  iam_role_permissions_boundary_arn = "arn:aws:iam::xxxxxxxxxxxx:policy/eo_role_boundary"
  eks_system_node_group_subnet_ids  = ["subnet-xxxxxxxxxxxx"]
  eks_additional_role_mapping = [
    {
      iam_role_arn  = "arn:aws:iam::xxxxxxxxxxxx:role/test-BastionExecutionRole"
      eks_role_name = "system:node:{{EC2PrivateDNSName}}"
      eks_groups    = ["system:bootstrappers", "system:nodes"]
    }
  ]
}  

```

versions.tf (if Cloud-Pipepline database configuration should be deployed):
```hcl
terraform {
  required_providers {
    postgresql = {
      source  = "cyrilgdn/postgresql"
      version = "1.21.0"
    }
  }
}
```

output.tf
```hcl
output "etc_bucket" {
  value = "SYSTEM_BUCKET_NAME=${module.cluster-infra.cp_etc_bucket}"
}

output "docker_bucket" {
  value = "CP_DOCKER_STORAGE_CONTAINER=${module.cluster-infra.cp_docker_bucket}"
}


output "cp_main_role" {
  value = "CP_MAIN_SERVICE_ROLE=${module.cluster-infra.cluster_cp_main_execution_role}"
}

output "eks_cluster_name" {
  value = "CP_KUBE_CLUSTER_NAME=${module.cluster-infra.cluster_name}"
}

output "eks_cluster_endpoint" {
  value = "CP_KUBE_EXTERNAL_HOST=${module.cluster-infra.cluster_endpoint}"
}

output "cp_s3_via_sts_role" {
  value = "CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE=${module.cluster-infra.cp_s3_via_sts_role}"
}

output "efs_filesystem_id" {
  value = "CP_SYSTEM_FILESYSTEM_ID=${module.cluster-infra.cp_efs_filesystem_id}" 
}

output "efs_filesystem_exec_role" {
  value = "CP_CSI_EXECUTION_ROLE=${module.cluster-infra.cp_efs_filesystem_exec_role}"
}

output "cp_kms_arn" {
  value = "CP_AWS_KMS_ARN=${module.cluster-infra.cp_kms_arn}"
}

output "cp_instance_sg" {
  value = "CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS=${module.cluster-infra.eks_cluster_primary_security_group_id}"
}

output "cp_http_access_sg" {
  value = "CP_EDGE_AWS_ELB_SG=${module.cluster-infra.https_access_security_group}"
}

output "cp_rds_address" {
  value = "PSG_HOST=${module.cluster-infra.rds_address}"
}

output "ssh_key_name" {
  value = "CP_PREF_CLUSTER_SSH_KEY_NAME=${module.cluster-infra.cp_ssh_rsa_key_pair.key_pair_name}"
}

output "cp_deploy_script" {
  value = <<-EOF
 ./pipectl install \
 -d "library/centos:7" \
 -dt aws-native \
 -jc \
 -env CP_MAIN_SERVICE_ROLE="${module.cp-test-eks-infra.cluster_cp_main_execution_role}" \
 -env CP_CSI_DRIVER_TYPE=efs \
 -env CP_SYSTEM_FILESYSTEM_ID="${module.cp-test-eks-infra.cp_efs_filesystem_id}" \
 -env CP_CSI_EXECUTION_ROLE="${module.cp-test-eks-infra.cp_efs_filesystem_exec_role}" \
 -env CP_DOCKER_DIST_SRV="quay.io/" \
 -env CP_AWS_KMS_ARN="${module.cp-test-eks-infra.cp_kms_arn}" \
 -env CP_PREF_CLUSTER_SSH_KEY_NAME="${module.cp-test-eks-infra.cp_ssh_rsa_key_pair.key_pair_name}" \
 -env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS="${module.cp-test-eks-infra.eks_cluster_primary_security_group_id}" \
 -env CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE="${module.cp-test-eks-infra.cp_s3_via_sts_role}" \
 -env CP_CLUSTER_SSH_KEY="/opt/root/ssh/cloud-pipeline-test-eks-key.pem" \
 -env CP_DOCKER_STORAGE_TYPE="obj" \
 -env CP_DOCKER_STORAGE_CONTAINER="${module.cp-test-eks-infra.cp_docker_bucket}" \
 -env CP_DEPLOYMENT_ID="cloud-pipeline-test-eks" \
 -env CP_CLOUD_REGION_ID="eu-west-1" \
 -env CP_KUBE_CLUSTER_NAME="${module.cp-test-eks-infra.cluster_name}" \
 -env CP_KUBE_EXTERNAL_HOST="${module.cp-test-eks-infra.cluster_endpoint}" \
 -env CP_KUBE_SERVICES_TYPE="ingress" \
 -env CP_EDGE_AWS_ELB_SCHEME="internet-facing" \
 -env CP_EDGE_AWS_ELB_SUBNETS="<public-subnet-id>" \
 -env CP_EDGE_AWS_ELB_EIPALLOCS="<user-ellastic-ip-id>" \
 -env CP_EDGE_AWS_ELB_SG="${module.cp-test-eks-infra.https_access_security_group},${module.cp-test-eks-infra.eks_cluster_primary_security_group_id}" \
 --external-host-dns \
 -env PSG_HOST="${module.cp-test-eks-infra.rds_address}" \
 -s cp-api-srv \
 -env CP_API_SRV_EXTERNAL_PORT=443 \
 -env CP_API_SRV_INTERNAL_PORT=443 \
 -env CP_API_SRV_EXTERNAL_HOST="<user-domain-name>" \
 -env CP_API_SRV_INTERNAL_HOST="<user-domain-name>" \
 -env CP_API_SRV_IDP_CERT_PATH="/opt/idp/pki" \
 -env CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME="<user-deployment-name>" \
 -env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME="${module.cp-test-eks-infra.cp_etc_bucket}" \
 -env CP_API_SRV_SSO_BINDING="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" \
 -env CP_API_SRV_SAML_ALLOW_ANONYMOUS_USER="true" \
 -env CP_API_SRV_SAML_AUTO_USER_CREATE="EXPLICIT" \
 -env CP_API_SRV_SAML_GROUPS_ATTRIBUTE_NAME="Group" \
 -env CP_HA_DEPLOY_ENABLED="true" \
-s cp-idp \
 -env CP_IDP_EXTERNAL_HOST="auth.<user-domain-name>" \
 -env CP_IDP_INTERNAL_HOST="auth.<user-domain-name>" \
 -env CP_IDP_EXTERNAL_PORT=443 \
 -env CP_IDP_INTERNAL_PORT=443 \
 -s cp-docker-registry \
 -env CP_DOCKER_EXTERNAL_PORT=443 \
 -env CP_DOCKER_INTERNAL_PORT=443 \
 -env CP_DOCKER_EXTERNAL_HOST="docker.<user-domain-name>" \
 -env CP_DOCKER_INTERNAL_HOST="docker.<user-domain-name>" \
 -env CP_DOCKER_STORAGE_ROOT_DIR="/docker-pub/" \
 -s cp-edge \
 -env CP_EDGE_EXTERNAL_PORT=443 \
 -env CP_EDGE_INTERNAL_PORT=443 \
 -env CP_EDGE_EXTERNAL_HOST="edge.<user-domain-name>" \
 -env CP_EDGE_INTERNAL_HOST="edge.<user-domain-name>" \
 -env CP_EDGE_WEB_CLIENT_MAX_SIZE=0 \
 -s cp-clair \
 -env CP_CLAIR_DATABASE_HOST="${module.cp-test-eks-infra.rds_address}" \
 -s cp-docker-comp \
 -env CP_DOCKER_COMP_WORKING_DIR="/cloud-pipeline/docker-comp/wd" \
 -s cp-search \
 -s cp-heapster \
 -s cp-dav \
 -env CP_DAV_AUTH_URL_PATH="webdav/auth-sso" \
 -env CP_DAV_MOUNT_POINT="/dav-mount" \
 -env CP_DAV_SERVE_DIR="/dav-serve" \
 -env CP_DAV_URL_PATH="webdav" \
 -s cp-gitlab-db \
 -env GITLAB_DATABASE_VERSION="12.18" \
 -s cp-git \
 -env CP_GITLAB_VERSION=15 \
 -env CP_GITLAB_SESSION_API_DISABLE="true" \
 -env CP_GITLAB_API_VERSION=4 \
 -env CP_GITLAB_EXTERNAL_PORT=443 \
 -env CP_GITLAB_INTERNAL_PORT=443 \
 -env CP_GITLAB_EXTERNAL_HOST="git.<user-domain-name>" \
 -env CP_GITLAB_INTERNAL_HOST="git.<user-domain-name>" \
 -env CP_GITLAB_EXTERNAL_URL="https://git.<user-domain-name>" \
 -env CP_GITLAB_IDP_CERT_PATH="/opt/idp/pki" \
 -s cp-git-sync \
 -s cp-billing-srv \
 -env CP_BILLING_DISABLE_GS="true" \
 -env CP_BILLING_DISABLE_AZURE_BLOB="true" \
 -env CP_BILLING_CENTER_KEY="billing-group"
  EOF
}

```

To configure `cluster-infrastructure` deployment, there is a list of variables that need to be passed:

| Name                                | Description                                                                                                                          |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `project_name`                      | Name of the deployment. Will be used as resource name prefix of the created resources (security groups, IAM roles etc.)              |
| `env`                               | Environment name for the deployment. Will be used as resource name prefix of the created resources (security groups, IAM roles etc.) |
| `vpc_id`                            | Id of the VCP to be used for deployment of the bastion instance.                                                                     |
| `subnet_ids`                        | Ids of the VCP subnets to be used for Cloud Pipeline EKS cluster, FS mount points, etc.                                              |
| `deploy_filesystem_type`            | (Optional) Option to create EFS or FSx Lustre filesystem: must be set efs or fsx. If empty, no FS will be created. Default efs.      |
| `iam_role_permissions_boundary_arn` | (Optional) Account specific role boundaries                                                                                          |
| `eks_system_node_group_subnet_ids`  | Ids of the VCP subnets to be used for EKS cluster Cloud Pipeline system node group.                                                  |
| `eks_additional_role_mapping`       | List of additional roles mapping for aws_auth map.                                                                                   |

If deploy will use DataBase based on AWS RDS and need to create additional databases for Cloud-Pipeline services then provide additional value:

 `cloud_pipeline_db_configuration` - Username with password and database, which will be created by Postgres provider. Username will be owner of the database. 
 Additional settings with Postgresql provider and versions.tf file must be set.

If not need in creation of the additional databases then set to false `create_cloud_pipeline_db_configuration`, the option to create or not additional database. 

If deploy will not use Database based on AWS RDS then set to false `deploy_rds`, the option to create or not RDS instance. 

### Output of `cluster-infrastructure` module

Terraform module cluster-infra has the following possible outputs:

| Name                                    | Description                                                                                          |
| --------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `cluster_id`                            | The ID of the created EKS cluster.                                                                   |
| `cluster_name`                          | The name of the created EKS cluster.                                                                 |
| `cluster_arn`                           | The ARN of the created EKS cluster.                                                                  |
| `cluster_endpoint`                      | The endpoint of the created EKS cluster.                                                             |
| `cluster_certificate_authority_data`    | The Certificate Authority of the created EKS cluster                                                 |
| `cluster_cp_system_node_execution_role` | The role of the cluster node for nodes from EKS cluster system node group.                           |
| `cluster_cp_worker_node_execution_role` | The role of the cluster node, for EKS cluster worker nodes which will be launched by Cloud-Pipeline. |
| `cp_ssh_rsa_key_pair`                   | RSA key pair created during Cloud-Pipeline deployment                                                |
| `cp_etc_bucket`                         | Cloud-pipeline etc bucket name                                                                       |
| `cp_docker_bucket`                      | Cloud-pipeline docker registry bucket name                                                           |
| `rds_root_pass_secret`                  | Id of the secretsmanager secret where password of the RDS root_user is stored                        |
| `rds_address`                           | The address of the RDS instance                                                                      |
| `rds_root_username`                     | Username of the RDS default user                                                                     |
| `rds_port`                              | The port on which the RDS instance accepts connections                                               |

In the file Output.tf change the `cluster-infra` to your deployment module name or leave as is if module name not changed.

5.  Mount created file system into instance. For example for efs instance in eu-west-1 region by using command:
````
sudo mount -t nfs -o nfsvers=4.1,rsize=1048576,wsize=1048576,hard,timeo=600,retrans=2,noresvport fs-xxxxxxxxxxx.efs.eu-west-1.amazonaws.com:/  /opt

````

### Cloud-pipeline deployment 
1. Download latest pipectl binary file.
2. Run commands in console or create and run script file like in example:

````
#!/bin/bash
#Install
export SYSTEM_BUCKET_NAME="xxxxxxxxxxxxxxxx"
export REGISTRY_BUCKET_NAME="xxxxxxxxxxxxxx"
export KMS_ARN="arn:aws:kms:eu-west-1:xxxxxxxxxxxx:key/xxxxxxxx"
./pipectl install \
 -d "library/centos:7" \
 -dt aws-native \
 -jc \
 -env CP_MAIN_SERVICE_ROLE="arn:aws:iam::xxxxxxxxxxxx:role/xxxxxxxxxxxx-CPExecutionRole" \
 -env CP_CSI_DRIVER_TYPE=efs \
 -env CP_SYSTEM_FILESYSTEM_ID="fs-xxxxxxxxxxxx" \
 -env CP_CSI_EXECUTION_ROLE="arn:aws:iam::xxxxxxxxxxxx:role/xxxxxxxxxxxx-efs_csi-ExecutionRole" \
 -env CP_DOCKER_DIST_SRV="quay.io/" \
 -env CP_AWS_KMS_ARN="arn:aws:kms:eu-west-1:xxxxxxxxxxxx:key/xxxxxxxxxxxx" \
 -env CP_PREF_CLUSTER_SSH_KEY_NAME="xxxxxxxxxxxx-key" \
 -env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS="sg-xxxxxxxxxxxx" \
 -env CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE="arn:aws:iam::xxxxxxxxxxxx:role/xxxxxxxxxxxx-S3viaSTSRole" \
 -env CP_CLUSTER_SSH_KEY="/opt/root/ssh/xxxxxxxxxxxx-key.pem" \
 -env CP_DOCKER_STORAGE_TYPE="obj" \
 -env CP_DOCKER_STORAGE_CONTAINER="$REGISTRY_BUCKET_NAME" \
 -env CP_DEPLOYMENT_ID="xxxxxxxxxxxx-eks" \
 -env CP_CLOUD_REGION_ID="eu-west-1" \
 -env CP_KUBE_CLUSTER_NAME="xxxxxxxxxxxx-eks-cluster" \
 -env CP_KUBE_EXTERNAL_HOST="https://xxxxxxxxxxxx.sk1.eu-west-1.eks.amazonaws.com" \
 -env CP_KUBE_SERVICES_TYPE="ingress" \
 -env CP_EDGE_AWS_ELB_SCHEME="internet-facing" \
 -env CP_EDGE_AWS_ELB_SUBNETS="subnet-xxxxxxxxxxxx" \
 -env CP_EDGE_AWS_ELB_EIPALLOCS="eipalloc-xxxxxxxxxxxx" \
 -env CP_EDGE_AWS_ELB_SG="sg-xxxxxxxxxxxx,sg-xxxxxxxxxxxx" \
 --external-host-dns \
 -env PSG_HOST="cloud-pipeline-rds.xxxxxxxxxxxx.eu-west-1.rds.amazonaws.com" \
 -s cp-api-srv \
 -env CP_API_SRV_EXTERNAL_PORT=443 \
 -env CP_API_SRV_INTERNAL_PORT=443 \
 -env CP_API_SRV_EXTERNAL_HOST="<user-domain-name>" \
 -env CP_API_SRV_INTERNAL_HOST="<user-domain-name>" \
 -env CP_API_SRV_IDP_CERT_PATH="/opt/idp/pki" \
 -env CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME="<user-deployment-name>" \
 -env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME="$SYSTEM_BUCKET_NAME" \
 -env CP_API_SRV_SSO_BINDING="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" \
 -env CP_WIN_CODE_SIGN_CERT_PASS="123456" \
 -env CP_WIN_CODE_SIGN_CERT_DESC="CloudPipelineCLI" \
 -env CP_WIN_CODE_SIGN_CERT_URL="https://<user-domain-name>/pipeline/" \
 -env CP_API_SRV_SAML_ALLOW_ANONYMOUS_USER="true" \
 -env CP_API_SRV_SAML_AUTO_USER_CREATE="EXPLICIT" \
 -env CP_API_SRV_SAML_GROUPS_ATTRIBUTE_NAME="Group" \
 -env CP_HA_DEPLOY_ENABLED="true" \
-s cp-idp \
 -env CP_IDP_EXTERNAL_HOST="auth.<user-domain-name>" \
 -env CP_IDP_INTERNAL_HOST="auth.<user-domain-name>" \
 -env CP_IDP_EXTERNAL_PORT=443 \
 -env CP_IDP_INTERNAL_PORT=443 \
 -s cp-docker-registry \
 -env CP_DOCKER_EXTERNAL_PORT=443 \
 -env CP_DOCKER_INTERNAL_PORT=443 \
 -env CP_DOCKER_EXTERNAL_HOST="docker.<user-domain-name>" \
 -env CP_DOCKER_INTERNAL_HOST="docker.<user-domain-name>" \
 -env CP_DOCKER_STORAGE_ROOT_DIR="/docker-pub/" \
 -s cp-edge \
 -env CP_EDGE_CLUSTER_RESOLVER="172.20.0.10" \
 -env CP_EDGE_EXTERNAL_PORT=443 \
 -env CP_EDGE_INTERNAL_PORT=443 \
 -env CP_EDGE_EXTERNAL_HOST="edge.<user-domain-name>" \
 -env CP_EDGE_INTERNAL_HOST="edge.<user-domain-name>" \
 -env CP_EDGE_WEB_CLIENT_MAX_SIZE=0 \
 -s cp-clair \
 -env CP_CLAIR_DATABASE_HOST="xxxxxxxxxxxx-rds.xxxxxxxxxxxx.eu-west-1.rds.amazonaws.com" \
 -s cp-docker-comp \
 -env CP_DOCKER_COMP_WORKING_DIR="/cloud-pipeline/docker-comp/wd" \
 -s cp-search \
 -s cp-heapster \
 -s cp-dav \
 -env CP_DAV_AUTH_URL_PATH="webdav/auth-sso" \
 -env CP_DAV_MOUNT_POINT="/dav-mount" \
 -env CP_DAV_SERVE_DIR="/dav-serve" \
 -env CP_DAV_URL_PATH="webdav" \
 -s cp-gitlab-db \
 -env GITLAB_DATABASE_VERSION="12.18" \
 -s cp-git \
 -env CP_GITLAB_VERSION=15 \
 -env CP_GITLAB_SESSION_API_DISABLE="true" \
 -env CP_GITLAB_EXTERNAL_PORT=443 \
 -env CP_GITLAB_INTERNAL_PORT=443 \
 -env CP_GITLAB_EXTERNAL_HOST="git.<user-domain-name>" \
 -env CP_GITLAB_INTERNAL_HOST="git.<user-domain-name>" \
 -env CP_GITLAB_EXTERNAL_URL="https://git.<user-domain-name>" \
 -env CP_GITLAB_IDP_CERT_PATH="/opt/idp/pki" \
 -s cp-git-sync \
 -s cp-billing-srv \
 -env CP_BILLING_DISABLE_GS="true" \
 -env CP_BILLING_DISABLE_AZURE_BLOB="true" \
 -env CP_BILLING_CENTER_KEY="billing-group"

````    

Where:

`CP_MAIN_SERVICE_ROLE` - EKS cluster main execution role 

`CP_CSI_DRIVER_TYPE` - Filesystem type that will be mounted in EKS, could be efs or fsx. 

`CP_SYSTEM_FILESYSTEM_ID` - Id of created bu terraform filesystem. |

`CP_CSI_EXECUTION_ROLE` - Execution role with permission to interact with filesystem, used by SCI driver 

`CP_PREF_CLUSTER_SSH_KEY_NAME` - Name of the ssh key. To save public part of the ssh key from terraform state use commands:

````
terraform show -json | jq -r ".values.root_module.child_modules[].resources[] |  select(.address==\"$(terraform state list | grep ssh_tls_key)\") |.values.private_key_pem"
````
Then copy content from -----BEGIN PUBLIC KEY----- to -----END PUBLIC KEY----- including these two lines into your "somefilename.pem"

`CP_CLUSTER_SSH_KEY` - Path to the specified above ssh key.

`CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS` - id of the internal cluster security group created during cluster deploy 

`CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE` - Role that generates temporary credentials to work with storage. 

`CP_KUBE_CLUSTER_NAME` - EKS cluster name. 

`CP_KUBE_EXTERNAL_HOST` - EKS cluster endpoint. 

`CP_EDGE_AWS_ELB_SCHEME` - Load Balance scheme

`CP_EDGE_AWS_ELB_SUBNETS`- Public subnet which will be used by Load Balancer

`CP_EDGE_AWS_ELB_EIPALLOCS` - Allocation ID of the created Elastic IP.
 
`CP_EDGE_AWS_ELB_SG` - Security group IDs for Load Balances, one provide access to EKS cluster internal network and another provide public access to LB from internet.

`PSG_HOST` - Address of the created RDS instance.

3.  Wait until deployment finishes.
4.  Check kubernetes pods status and connection to resources. 