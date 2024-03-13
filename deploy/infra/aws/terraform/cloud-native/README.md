# Cloud-pipeline based on AWS EKS Deployment step-by-step guide

## Overview

This document provides a guidance how to deploy infrastructure using terraform and install Cloud-Pipeline on top of it.
The process of the deployment can be performed with the following steps:

- [Cloud-pipeline based on AWS EKS Deployment step-by-step guide](#cloud-pipeline-based-on-aws-eks-deployment-step-by-step-guide)
  - [Overview](#overview)
  - [Resources deployment using Terraform](#resources-deployment-using-terraform)
    - [Prerequisites](#prerequisites)
    - [Jump-server deployment](#jump-server-deployment)
      - [Outputs table of `jump-server` module](#outputs-table-of-jump-server-module)
    - [Cluster-infrastructure deployment](#cluster-infrastructure-deployment)
      - [Outputs table of `cluster-infrastructure` module](#outputs-table-of-cluster-infrastructure-module)
  - [Cloud-pipeline deployment](#cloud-pipeline-deployment)

>This guide uses best practice approach when code for resource creation stores in users private Git repository. 

## Resources deployment using Terraform

To get started with deployment, please make sure that you satisfy requirements below.

### Prerequisites

| Name                                                                      | Version |
| ------------------------------------------------------------------------- | ------- |
| <a name="requirement_terraform"></a> [terraform](#requirement\_terraform) | = 1.5.0 |

To install terraform 1.50 on Linux amd64 type of OS you can run commands:
```
    sudo wget https://releases.hashicorp.com/terraform/1.5.0/terraform_1.5.0_linux_amd64.zip
    sudo unzip terraform_1.5.0_linux_amd64.zip 
    chmod +x terraform
    sudo mv terraform /usr/local/bin/
    sudo rm terraform_1.5.0_linux_amd64.zip
```

 To install terraform on other operating sysytem please follow the links 
 https://developer.hashicorp.com/terraform/tutorials/aws-get-started/install-cli <br>
 https://developer.hashicorp.com/terraform/install

1. Manually create S3 Bucket to store remote state of the terraform deployment.
2. Manually create DynamoDB table to store terraform lock records.
   - Table schema:
   ```
      LockID (String) - Partition key
   ```
> The following resources are dependencies and should be created in advance:
> * VPC
> *	Private subntes (where all infrastructure will be create: EKS cluster, RDS instance, FS, etc)
> *	Mechanism to have inbound access to the VPC (IGW, transit gateway, VPN from corporate network etc.)


### Jump-server deployment 

To deploy required resources in your environment with terraform, please follow these steps:
1. Create directory named for example `jump-server` in your environment deployment location and place Terraform files there: main.tf and output.tf.

Example of the deployment `Jump-server` files:

main.tf
```hcl
terraform {
  backend "s3" {
    bucket         = "xxxxxxxxxxxx-infra"
    key            = "xxxxxx-jumpbox/terraform.tfstate"
    region         = "<region>"
    encrypt        = true
    dynamodb_table = "xxxxxxxxxxxx-infra"
  }
  required_version = "1.5.0"
}

provider "aws" {
  region = "<region>"
}

module jump-server {
    source = "git::https://github.com/epam/cloud-pipeline//deploy/infra/aws/terraform/cloud-native/jump-server?ref=<branch-tag-or-commit>"
    project_name                      = "xxxxxxxxxxxx"
    env                               = "xxxxxxx"
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
> Change xxxxxxxxxxxx to values that described is list of the variables:

| Name                                | Description                                                                                                                          |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `bucket`                            | Name of the created S3 bucket to store terraform state file. See the [prerequisites](#prerequisites)                                 |
| `dynamodb_table`                    | Name of the created DynamoDB table for terraform. See the [prerequisites](#prerequisites)                                            |
| `project_name`                      | Name of the deployment. Will be used as resource name prefix of the created resources (security groups, iam roles etc.)              |
| `env`                               | Environment name for the deployment. Will be used as resource name prefix of the created resources (security groups, IAM roles etc.) |
| `vpc_id`                            | Id of the VCP to be used for deployment of the bastion instance.                                                                     |
| `subnet_id`                         | Id of the VCP subnet to be used to launch an instance                                                                                |
| `ami_id`                            | (Optional) AMI to be used for bastion ec2 instance. If empty - eks-optimized will be used.                                           |
| `iam_role_permissions_boundary_arn` | (Optional) Account specific role boundaries                                                                                          |

1. Push created configuration in to your git repository.
2. From `jump-server` directory run `terraform init`command, output of command must be like this:

```
Terraform has been successfully initialized!

You may now begin working with Terraform. Try running "terraform plan" to see
any changes that are required for your infrastructure. All Terraform commands
should now work.
```

4. After successful output of the init command run `terraform apply` and when it shows list of the planned for creation resources submit with **yes**.

Example of the **apply** output:

```
Apply complete! Resources: .....
Outputs:
instance_connection = "Login to Jump Server with command: aws ssm start-session --target i-xxxxxxxxxxx --region <region>"
instance_id = "i-xxxxxxxxxxxx"
instance_role = "arn:aws:iam::xxxxxxxxxxx:role/xxxxxxxxxxx_BastionExecutionRole"
```

#### Outputs table of `jump-server` module

| Name                  | Description                                                                                                                           |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `instance_connection` | Id of created Jump Server instance.                                                                                                   |
| `instance_id`         | Login to Jump Server with command: aws ssm start-session --target ${module.ec2_instance.id} --region ${data.aws_region.current.name}. |
| `instance_role`       | ARN of bastion execution role that must be set in EKS deployment module                                                               |

>User can call terraform output again by run command:

```hcl
   terraform output <output name from table above>
```
> Note: In most cases this command will only show output after resources were deployed with terraform apply command.

### Cluster-infrastructure deployment 

1. Create directory named for example `cluster-infrastructure` and place your Terraform files: main.tf, output.tf and if additional databases will be deployed - versions.tf.

Example of the `cluster-infrastructure` files deployment:

main.tf
```hcl
terraform {
  backend "s3" {
    bucket         = "xxxxxxxxxxxx-infra"
    key            = "eks/terraform.tfstate"
    region         = "<region>"
    encrypt        = true
    dynamodb_table = "xxxxxxxxxxxx-infra"
  }
  required_version = "1.5.0"
}

provider "aws" {
  region = "<region>"
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
  env                               = "xxxx"
  vpc_id                            = "vpc-xxxxxxxxxxxx"
  cp_api_access_prefix_lists        = ["pl-xxxxxxxxxxxx"]
  subnet_ids                        = ["subnet-xxxxxxxxxxxx", "subnet-xxxxxxxxxxxx", "subnet-xxxxxxxxxxxx"]
  iam_role_permissions_boundary_arn = "arn:aws:iam::xxxxxxxxxxxx:policy/eo_role_boundary"
  eks_system_node_group_subnet_ids  = ["subnet-xxxxxxxxxxxx"]
  eks_additional_role_mapping = [
    {
      iam_role_arn  = "arn:aws:iam::xxxxxxxxxxxx:role/xxxxxxxxxx-BastionExecutionRole"
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
 -env CP_CLUSTER_SSH_KEY="/opt/root/ssh/ssh-key.pem" \
 -env CP_DOCKER_STORAGE_TYPE="obj" \
 -env CP_DOCKER_STORAGE_CONTAINER="${module.cp-test-eks-infra.cp_docker_bucket}" \
 -env CP_DEPLOYMENT_ID="<users-deployment-name>" \
 -env CP_CLOUD_REGION_ID="<region>" \
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
 -env CP_GITLAB_API_VERSION=v4 \
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
To configure `cluster-infrastructure` deployment, there is a list of variables that need to be specified:

| Name                                     | Description                                                                                                                                                                                                                                                                                                |
| ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bucket`                                 | Name of the created S3 bucket to store terraform state file. See the [prerequisites](#prerequisites)                                                                                                                                                                                                       |
| `dynamodb_table`                         | Name of the created DynamoDB table for terraform. See the [prerequisites](#prerequisites)                                                                                                                                                                                                                  |
| `project_name`                           | Name of the deployment. Will be used as resource name prefix of the created resources (security groups, IAM roles etc.)                                                                                                                                                                                    |
| `env`                                    | Environment name for the deployment. Will be used as resource name prefix of the created resources (security groups, IAM roles etc.)                                                                                                                                                                       |
| `vpc_id`                                 | Id of the VCP to be used for deployment of the bastion instance.                                                                                                                                                                                                                                           |
| `subnet_ids`                             | Ids of the VCP subnets to be used for Cloud Pipeline EKS cluster, FS mount points, etc.                                                                                                                                                                                                                    |
| `deploy_filesystem_type`                 | (Optional) Option to create EFS or FSx Lustre filesystem: must be set efs or fsx. If empty, no FS will be created. Default efs.                                                                                                                                                                            |
| `iam_role_permissions_boundary_arn`      | (Optional) Account specific role boundaries                                                                                                                                                                                                                                                                |
| `eks_system_node_group_subnet_ids`       | Ids of the VCP subnets to be used for EKS cluster Cloud Pipeline system node group.                                                                                                                                                                                                                        |
| `eks_additional_role_mapping`            | List of additional roles mapping for aws_auth map.                                                                                                                                                                                                                                                         |
| `cloud_pipeline_db_configuration`        | (Optional) Username with password and database, which will be created. Username will be owner of the database. Additional settings with Postgresql provider and versions.tf file must be set. For example see [main.tf](#cluster-infrastructure-deployment) of the cluster deployment |
| `create_cloud_pipeline_db_configuration` | (Optional) You can disable creation of the additional databases by setting to false |                                                                                                                          
| `deploy_rds`                             | (Optional) You can disable deployment of the RDS instance by setting deploy_rds = false. In this case no db configuration will be created regardless the value of create_cloud_pipeline_db_configuration  |


1. Push created configuration in to your git repository.
2. Install aws ssm manager: https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html
3. Connect to created jump-server instance using command like: 
```
aws ssm start-session --target i-xxxxxxxxxxxxx --region <region>
```
Where xxxxxxxx is your jump-server instance ID that could be found(also with full command) from [output](#output-of-jump-server-module) of the `terraform apply` jump-server deployment. 

1. Clone from your git repository pushed previously configuration.
2. From `cluster-infrastructure` directory run `terraform init`command, output of command must be like this:

```
Terraform has been successfully initialized!

You may now begin working with Terraform. Try running "terraform plan" to see
any changes that are required for your infrastructure. All Terraform commands
should now work.
```

4. After successful output of the init command run `terraform apply` and when it shows list of the planned for creation resources submit with **yes**.

Example of the **apply** output:

```
Apply complete! Resources: .....

Outputs:

cp_deploy_script = <<EOT
 ./pipectl install \
 -d "library/centos:7" \
 -dt aws-native \
 -jc \
 -env CP_MAIN_SERVICE_ROLE="arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxxCPExecutionRole" \
 -env CP_CSI_DRIVER_TYPE=efs \
 -env CP_SYSTEM_FILESYSTEM_ID="fs-xxxxxxxxxxxxxxx" \
 -env CP_CSI_EXECUTION_ROLE="arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxx-efs_csi-ExecutionRole" \
 -env CP_DOCKER_DIST_SRV="quay.io/" \
 -env CP_AWS_KMS_ARN="arn:aws:kms:<region>:xxxxxxxxxxxxxxx:key/xxxxxxxxxxxxxxx" \
 -env CP_PREF_CLUSTER_SSH_KEY_NAME="xxxxxxxxxxxxxxx-key" \
 -env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS="sg-xxxxxxxxxxxxxxx" \
 -env CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE="arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxxS3viaSTSRole" \
 -env CP_CLUSTER_SSH_KEY="/opt/root/ssh/ssh-key.pem" \
 -env CP_DOCKER_STORAGE_TYPE="obj" \
 -env CP_DOCKER_STORAGE_CONTAINER="xxxxxxxxxxxxxxx-docker" \
 -env CP_DEPLOYMENT_ID="xxxxxxxxxxxxxxx" \
 -env CP_CLOUD_REGION_ID="<region>" \
 -env CP_KUBE_CLUSTER_NAME="xxxxxxxxxxxxxxx-cluster" \
 -env CP_KUBE_EXTERNAL_HOST="https://xxxxxxxxxxxxxxx.gr7.<region>.eks.amazonaws.com" \
 -env CP_KUBE_SERVICES_TYPE="ingress" \
 -env CP_EDGE_AWS_ELB_SCHEME="internet-facing" \
 -env CP_EDGE_AWS_ELB_SUBNETS="subnet-xxxxxxxxxxxxxxx" \
 -env CP_EDGE_AWS_ELB_EIPALLOCS="eipalloc-xxxxxxxxxxxxxxx" \
 -env CP_EDGE_AWS_ELB_SG="sg-xxxxxxxxxxxxxxx,sg-xxxxxxxxxxxxxxx" \
 --external-host-dns \
 -env PSG_HOST="xxxxxxxxxxxxxxx-rds.xxxxxxxxxxxxxxx.<region>.rds.amazonaws.com" \
 -s cp-api-srv \
 -env CP_API_SRV_EXTERNAL_PORT=443 \
 -env CP_API_SRV_INTERNAL_PORT=443 \
 -env CP_API_SRV_EXTERNAL_HOST="<user-domain-name>" \
 -env CP_API_SRV_INTERNAL_HOST="<user-domain-name>" \
 -env CP_API_SRV_IDP_CERT_PATH="/opt/idp/pki" \
 -env CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME="<users deployment name>" \
 -env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME="xxxxxxxxxxxxxxx-etc" \
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
 -env CP_CLAIR_DATABASE_HOST="xxxxxxxxxxxxxxx.xxxxxxxxxxxxxxx.<region>.rds.amazonaws.com" \
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
 -env CP_GITLAB_API_VERSION=v4 \
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

EOT
cp_http_access_sg = "CP_EDGE_AWS_ELB_SG=sg-xxxxxxxxxxxxxxx"
cp_instance_sg = "CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS=sg-xxxxxxxxxxxxxxx"
cp_kms_arn = "CP_AWS_KMS_ARN=arn:aws:kms:<region>:xxxxxxxxxxxxxxx:key/xxxxxxxxxxxxxxx"
cp_main_role = "CP_MAIN_SERVICE_ROLE=arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxxCPExecutionRole"
cp_rds_address = "PSG_HOST=xxxxxxxxxxxxxxx-rds.xxxxxxxxxxxxxxx.<region>.rds.amazonaws.com"
cp_s3_via_sts_role = "CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE=arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxxS3viaSTSRole"
docker_bucket = "CP_DOCKER_STORAGE_CONTAINER=xxxxxxxxxxxxxxx-docker"
efs_filesystem_exec_role = "CP_CSI_EXECUTION_ROLE=arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxx-efs_csi-ExecutionRole"
efs_filesystem_id = "CP_SYSTEM_FILESYSTEM_ID=fs-xxxxxxxxxxxxxxx"
eks_cluster_endpoint = "CP_KUBE_EXTERNAL_HOST=https://xxxxxxxxxxxxxxx.gr7.<region>.eks.amazonaws.com"
eks_cluster_name = "CP_KUBE_CLUSTER_NAME=xxxxxxxxxxxxxxx-cluster"
etc_bucket = "SYSTEM_BUCKET_NAME=xxxxxxxxxxxxxxx-etc"
ssh_key_name = "CP_PREF_CLUSTER_SSH_KEY_NAME=xxxxxxxxxxxxxxx-key"
```

#### Outputs table of `cluster-infrastructure` module

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
| `cp_deploy_script`                      | Example of the pipeline install script with all necessary values from infrastructure deployment      |

>User can call terraform output again by run command:

```hcl
   terraform output <output name from table above>
```
> Note: In most cases this command will only show output after resources were deployed with terraform apply command.

## Cloud-pipeline deployment

1. Download latest pipectl binary file.
2. Mount created file system into instance. For example for efs instance by using command:
````
sudo mount -t nfs -o nfsvers=4.1,rsize=1048576,wsize=1048576,hard,timeo=600,retrans=2,noresvport fs-xxxxxxxxxxx.efs.<region>.amazonaws.com:/  /opt
````
3. Create ssh key from `cluster-infrastructure` deployment:
```
sudo mkdir -p /opt/root/ssh

terraform show -json | jq -r ".values.root_module.child_modules[].resources[] |  select(.address==\"$(terraform state list | grep ssh_tls_key)\") |.values.private_key_pem" > /opt/root/ssh/ssh-key.pem
```
4. Take script from the `cluster-infrastructure` deployment [output](#output-of-cluster-infrastructure-module) and change xxxxxxxxxx values that not added automatically. For example:

`user-domain-name` - domain name that user created using own Domain name provider.

`region `- AWS region id where resources deployed.

`CP_CSI_DRIVER_TYPE` - Filesystem type that will be mounted in EKS, could be efs or fsx. 

`CP_EDGE_AWS_ELB_EIPALLOCS` - Allocation ID of the created Elastic IP.

`CP_CLOUD_REGION_ID` - AWS region id where resources deployed.

`CP_API_SRV_EXTERNAL_HOST`, `CP_API_SRV_INTERNAL_HOST` and other DNS names of the services.
 

5.  Wait until deployment finishes.
6.  Check kubernetes pods status and connection to resources. 