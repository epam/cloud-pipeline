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

> This guide assumes that you store your terraform root modules in a private git repository and able to clone it from
> remote instance.

## Resources deployment using Terraform

To get started with deployment, please make sure that you satisfy requirements below.

### Prerequisites

| Name                                                                      | Version |
|---------------------------------------------------------------------------|---------|
| <a name="requirement_terraform"></a> [terraform](#requirement\_terraform) | = 1.5.0 |

To install terraform 1.50 on Linux amd64 type of OS you can run commands:

```
    sudo wget https://releases.hashicorp.com/terraform/1.5.0/terraform_1.5.0_linux_amd64.zip
    sudo unzip terraform_1.5.0_linux_amd64.zip 
    chmod +x terraform
    sudo mv terraform /usr/local/bin/
    sudo rm terraform_1.5.0_linux_amd64.zip
```

To install terraform to other operating system please follow the links
https://developer.hashicorp.com/terraform/tutorials/aws-get-started/install-cli <br>
https://developer.hashicorp.com/terraform/install

1. Manually create S3 Bucket to store remote state of the terraform deployment.
   To create S3 bucket you can use AWS Console or aws cli commands:
   a. If region us-east-1
   ```
   aws s3api create-bucket --bucket <s3-bucket-for-terraform-state-name-example> 
   ```

b. If another region

  ```
  aws s3api create-bucket --region <your deploy aws region>  --bucket <s3-bucket-for-terraform-state-name-example> --create-bucket-configuration LocationConstraint=<your deploy aws region>
  ```

1. Manually create DynamoDB table to store terraform lock records.
    - Table schema:
   ```
      LockID (String) - Partition key
   ```

To create DynamoDB table you can use AWS Console or aws cli command:

   ```
   aws dynamodb create-table --table-name <dynamobd-table-to-store-terraform-lock-name-example> --attribute-definitions AttributeName=LockID,AttributeType=S --key-schema AttributeName=LockID,KeyType=HASH --provisioned-throughput ReadCapacityUnits=5,WriteCapacityUnits=5 --region <your deploy aws region>
   ```

> The following resources are dependencies and should be created in advance:
> * VPC in your AWS account
> * Private subnets (where all infrastructure will be created: EKS cluster, RDS instance, FS, etc.)
> * Mechanism to have inbound access to the VPC (IGW, transit gateway, VPN from corporate network etc.)

3. AWS Elastic IP allocation. <br>
   Create AWS Elastic IP allocation to provide this value further during Cloud-Pipeline installation.
   This value will be used to deploy AWS ELB in your account to route the traffic from users to Cloud-Pipeline services
   This EIP also should be used to request DNS records creation from you DNS provider. The following scheme of the
   records is proposed:

| DNS record                                 | Record type | Value                               |
|--------------------------------------------|-------------|-------------------------------------|
| <cloud-pipeline-name>.<your-domain>        | A           | < EIP value >                       |
| edge.<cloud-pipeline-name>.<your-domain>   | CNAME       | <cloud-pipeline-name>.<your-domain> |
| docker.<cloud-pipeline-name>.<your-domain> | CNAME       | <cloud-pipeline-name>.<your-domain> |
| git.<cloud-pipeline-name>.<your-domain>    | CNAME       | <cloud-pipeline-name>.<your-domain> |

### Jump-server deployment

To deploy required resources in your environment with terraform, please follow these steps:

1. In the Git repo where you would like to store you terraform code: create directory named, for example,
   `jump-server` and place Terraform files there: main.tf and output.tf.

Templates of the deployment `Jump-server` files:

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
  source                            = "git::https://github.com/epam/cloud-pipeline//deploy/infra/aws/terraform/cloud-native/jump-server?ref=<branch-tag-or-commit>"
  project_name                      = "xxxxxxxxxxxx"
  env                               = "xxxxxxx"
  vpc_id                            = "vpc-xxxxxxxxxxxx"
  subnet_id                         = "subnet-xxxxxxxxxxxx"
  iam_role_permissions_boundary_arn = "arn:aws:iam::xxxxxxxxxxxx:policy/eo_role_boundary"
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
|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `bucket`                            | Name of the created S3 bucket to store terraform state file. See the [prerequisites](#prerequisites)                                 |
| `dynamodb_table`                    | Name of the created DynamoDB table for terraform. See the [prerequisites](#prerequisites)                                            |
| `deployment_name`                   | Name of the deployment. Will be used as resource name prefix of the created resources (security groups, iam roles etc.)              |
| `deployment_env`                    | Environment name for the deployment. Will be used as resource name prefix of the created resources (security groups, IAM roles etc.) |
| `vpc_id`                            | Id of the VCP to be used for deployment of the bastion instance.                                                                     |
| `subnet_id`                         | Id of the VCP subnet to be used to launch an instance                                                                                |
| `ami_id`                            | (Optional) AMI to be used for bastion ec2 instance. If empty - eks-optimized will be used.                                           |
| `iam_role_permissions_boundary_arn` | (Optional) Account specific role boundaries which will be applied during jump-server instance profile creation                       |

2. Push created configuration in to your git repository.
3. From `jump-server` directory run `terraform init`command, output of command must be like this:

```
Terraform has been successfully initialized!

You may now begin working with Terraform. Try running "terraform plan" to see
any changes that are required for your infrastructure. All Terraform commands
should now work.
```

4. After successful output of the terraform init command run `terraform apply` and when it shows list of the planned for
   creation
   resources submit with **yes**.

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
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `instance_connection` | Id of created Jump Server instance.                                                                                                   |
| `instance_id`         | Login to Jump Server with command: aws ssm start-session --target ${module.ec2_instance.id} --region ${data.aws_region.current.name}. |
| `instance_role`       | ARN of bastion execution role that must be set in EKS deployment module                                                               |

> User can call terraform output again by run command:

```hcl
   terraform output <output name from table above>
```

> Note: In most cases this command will only show output after resources were deployed with terraform apply command.

### Cluster-infrastructure deployment

1. In the Git repo where you would like to store you terraform code: create directory named, for example,
   `cluster-infrastructure` and place your Terraform files: main.tf, output.tf and if you would like to deploy
   cloud-pipeline databases configuration - versions.tf.

Template of the `cluster-infrastructure` files deployment:

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
    command = "aws"
    # This requires the awscli to be installed locally where Terraform is executed
    args        = ["eks", "get-token", "--cluster-name", module.cluster-infra.cluster_name]
  }
}

provider "helm" {
  kubernetes {
    host                   = module.cluster-infra.cluster_endpoint
    cluster_ca_certificate = base64decode(module.cluster-infra.cluster_certificate_authority_data)

    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command = "aws"
      # This requires the awscli to be installed locally where Terraform is executed
      args        = ["eks", "get-token", "--cluster-name", module.cluster-infra.cluster_name]
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
  source                             = "git::https://github.com/epam/cloud-pipeline//deploy/infra/aws/terraform/cloud-native/cluster-infra?ref=<branch-tag-or-commit>"
  deployment_name                    = "xxxxxxxxxxxx"
  deployment_env                     = "xxxxxxxxxxxx"
  vpc_id                             = "vpc-xxxxxxxxxxxx"
  cp_api_access_prefix_lists         = ["pl-xxxxxxxxxxxx"]
  external_access_security_group_ids = ["xxxxxxxxxxxx"]
  subnet_ids                         = ["subnet-xxxxxxxxxxxx", "subnet-xxxxxxxxxxxx", "subnet-xxxxxxxxxxxx"]
  iam_role_permissions_boundary_arn  = "arn:aws:iam::xxxxxxxxxxxx:policy/eo_role_boundary"
  eks_system_node_group_subnet_ids   = ["subnet-xxxxxxxxxxxx"]
  deploy_filesystem_type             = "xxxxxxxxxxxx"
  cp_deployment_id                   = "xxxxxxxxxxxx"
  cp_edge_elb_schema                 = "xxxxxxxxxxxx"
  cp_edge_elb_subnet                 = "xxxxxxxxxxxx"
  cp_edge_elb_ip                     = "xxxxxxxxxxxx"
  cp_api_srv_host                    = "xxxxxxxxxxxx"
  cp_idp_host                        = "xxxxxxxxxxxx"
  cp_docker_host                     = "xxxxxxxxxxxx"
  cp_edge_host                       = "xxxxxxxxxxxx"
  cp_gitlab_host                     = "xxxxxxxxxxxx"
  eks_additional_role_mapping        = [
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
output "filesystem_mount" {
  value = module.cluster-infra.cp_filesystem_mount_point
}

output "filesystem_type" {
  value = module.cluster-infra.deploy_filesystem_type
}

output "cp_pipectl_script" {
  value = module.cluster-infra.cp_deploy_script
}


```

To configure `cluster-infrastructure` deployment, there is a list of variables that need to be specified:

| Name                                     | Description                                                                                                                                                                                                                                                                           |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `bucket`                                 | Name of the created S3 bucket to store terraform state file. See the [prerequisites](#prerequisites)                                                                                                                                                                                  |
| `dynamodb_table`                         | Name of the created DynamoDB table for terraform. See the [prerequisites](#prerequisites)                                                                                                                                                                                             |
| `deployment_name`                        | Name of the deployment. Will be used as resource name prefix of the created resources (security groups, IAM roles etc.)                                                                                                                                                               |
| `deployment_env`                         | Environment name for the deployment. Will be used as resource name prefix of the created resources (security groups, IAM roles etc.)                                                                                                                                                  |
| `vpc_id`                                 | Id of the VCP to be used for deployment of the infrastructure.                                                                                                                                                                                                                        |
| `subnet_ids`                             | Ids of the VCP subnets to be used for Cloud Pipeline EKS cluster, FS mount points, etc.                                                                                                                                                                                               |
| `eks_system_node_group_subnet_ids`       | Ids of the VCP subnets to be used for EKS cluster Cloud Pipeline system node group.                                                                                                                                                                                                   |
| `eks_additional_role_mapping`            | List of additional roles mapping for aws_auth map.                                                                                                                                                                                                                                    |
| `deploy_filesystem_type`                 | (Optional) Option to create EFS or FSx Lustre filesystem: must be set efs or fsx. If empty, no FS will be created. Default efs.                                                                                                                                                       |
| `cloud_pipeline_db_configuration`        | (Optional) Username with password and database, which will be created. Username will be owner of the database. Additional settings with Postgresql provider and versions.tf file must be set. For example see [main.tf](#cluster-infrastructure-deployment) of the cluster deployment |
| `iam_role_permissions_boundary_arn`      | (Optional) Account specific role boundaries which will be applied during jump-server instance profile creation                                                                                                                                                                        |
| `deploy_rds`                             | (Optional) You can disable deployment of the RDS instance by setting deploy_rds = false. In this case no db configuration will be created regardless the value of create_cloud_pipeline_db_configuration                                                                              |
| `create_cloud_pipeline_db_configuration` | (Optional) You can disable creation of the cloud-pipeline database configuration by setting to false                                                                                                                                                                                  |
| `cp_deployment_id`                       | (Optional) Specify unique ID of the Cloud-Pipeline deployment. It will be used to name cloud entities (e.g. path within a docker registry object container).Must contain only letters, digits, underscore or dash                                                                     |   
| `cp_edge_elb_schema`                     | (Required) Type of the AWS ELB to provide access to the users to the system. Possible values 'internal', 'internet-facing'. Default 'internet-facing'.                                                                                                                                |
| `cp_edge_elb_subnet`                     | (Required) The ID of the public subnet for the Load Balancer to be created. Must be in the same Availability Zone (AZ) as the CPSystemSubnetId                                                                                                                                        |
| `cp_edge_elb_ip`                         | (Required) Allocation ID of the Elastic IP from prerequisites in case of internet-facing ELB, or private IP in case of internal ELB.                                                                                                                                                  |
| `cp_api_srv_host`                        | (Required) API service domain name address.                                                                                                                                                                                                                                           |
| `cp_idp_host`                            | (Optional) Self hosted IDP service domain name address. WARNING: Using self hosted IDP service in production environment strongly not recommended!                                                                                                                                    |                      
| `cp_docker_host`                         | (Required) Docker service domain name address.                                                                                                                                                                                                                                        |
| `cp_edge_host`                           | (Required) EDGE service domain name address.                                                                                                                                                                                                                                          |
| `cp_gitlab_host`                         | (Required) GITLAB service domain name address.                                                                                                                                                                                                                                        |                                                                                                                     

1. Push created configuration in to your git repository.
2. Install aws ssm
   manager: https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html
3. Connect to created jump-server instance using command like:

```
aws ssm start-session --target i-xxxxxxxxxxxxx --region <region>
```

Where xxxxxxxx is your jump-server instance ID that could be found(also with full command)
from [output](#output-of-jump-server-module) of the `terraform apply` jump-server deployment.

5. Clone from your git repository pushed previously configuration.
6. From `cluster-infrastructure` directory run `terraform init`command, output of command must be like this:

```
Terraform has been successfully initialized!

You may now begin working with Terraform. Try running "terraform plan" to see
any changes that are required for your infrastructure. All Terraform commands
should now work.
```

7. After successful output of the init command run `terraform apply` and when it shows list of the planned for creation
   resources submit with **yes**.

The output can be different depending on terraform options like cp_idp_host or enable_aws_omics_integration.

Example of the **apply** output:

```
Apply complete! Resources: .....

Outputs:

cp_pipectl_script = <<EOT
./pipectl install \
-dt aws-native \
-jc \
-env CP_MAIN_SERVICE_ROLE="arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxxCPExecutionRole" \
-env CP_CSI_DRIVER_TYPE="efs" \
-env CP_SYSTEM_FILESYSTEM_ID="fs-xxxxxxxxxxxxxxx" \
-env CP_SYSTEM_FILESYSTEM_MOUNTNAME="" \
-env CP_CSI_EXECUTION_ROLE="arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxx-ExecutionRole" \
-env CP_DOCKER_DIST_SRV="quay.io/" \
-env CP_AWS_KMS_ARN="arn:aws:kms:xxxxxxxxxxxxxxx:xxxxxxxxxxxxxxx:key/xxxxxxxxxxxxxxx" \
-env CP_PREF_CLUSTER_SSH_KEY_NAME="xxxxxxxxxxxxxxx" \
-env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS="sg-xxxxxxxxxxxxxxx" \
-env CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE="arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxxS3viaSTSRole" \
-env CP_CLUSTER_SSH_KEY="/opt/root/ssh/ssh-key.pem" \
-env CP_DOCKER_STORAGE_TYPE="obj" \
-env CP_DOCKER_STORAGE_CONTAINER="xxxxxxxxxxxxxxx-docker" \
-env CP_DEPLOYMENT_ID="<users deployment id>" \
-env CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME="<users deployment name>" \
-env CP_CLOUD_REGION_ID="xxxxxxxxxxxxxxx" \
-env CP_KUBE_CLUSTER_NAME="xxxxxxxxxxxxxxx-eks-cluster" \
-env CP_KUBE_EXTERNAL_HOST="https://xxxxxxxxxxxxxxx.gr7.xxxxxxxxxxxxxxx.eks.amazonaws.com" \
-env CP_KUBE_SERVICES_TYPE="ingress" \
--external-host-dns \
-env PSG_HOST="xxxxxxxxxxxxxxx-rds.xxxxxxxxxxxxxxx.xxxxxxxxxxxxxxx.rds.amazonaws.com" \
-env PSG_PASS="pipeline" \
-env PSG_CONNECT_PARAMS="?ssl=true&sslfactory=org.postgresql.ssl.NonValidatingFactory" \
-s cp-api-srv \
-env CP_API_SRV_EXTERNAL_PORT=443 \
-env CP_API_SRV_INTERNAL_PORT=443 \
-env CP_API_SRV_EXTERNAL_HOST="<user-domain-name>" \
-env CP_API_SRV_INTERNAL_HOST="<user-domain-name>" \
-env CP_API_SRV_IDP_CERT_PATH="/opt/idp/pki" \
-env CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME="<users deployment name>" \
-env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME="xxxxxxxxxxxxxxx" \
-env CP_API_SRV_SSO_BINDING="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" \
-env CP_API_SRV_SAML_ALLOW_ANONYMOUS_USER="true" \
-env CP_API_SRV_SAML_AUTO_USER_CREATE="EXPLICIT" \
-env CP_API_SRV_SAML_GROUPS_ATTRIBUTE_NAME="Group" \
-env CP_HA_DEPLOY_ENABLED="true" \
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
-env CP_CLAIR_DATABASE_HOST="xxxxxxxxxxxxxxx-rds.xxxxxxxxxxxxxxx.xxxxxxxxxxxxxxx.rds.amazonaws.com" \
-env CP_CLAIR_DATABASE_PASSWORD="clair" \
-env CP_CLAIR_DATABASE_SSL_MODE="require" \
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
-env CP_BILLING_CENTER_KEY="billing-group" \
-s cp-idp \
-env CP_IDP_EXTERNAL_HOST="auth.<user-domain-name>" \
-env CP_IDP_INTERNAL_HOST="auth.<user-domain-name>" \
-env CP_IDP_EXTERNAL_PORT=443 \
-env CP_IDP_INTERNAL_PORT=443 \
-env CP_PREF_AWS_OMICS_SERVICE_ROLE=arn:aws:iam::xxxxxxxxxxxxxxx:role/xxxxxxxxxxxxxxxOmicsServiceRole \
-env CP_PREF_AWS_OMICS_ECR_REGISTRY=xxxxxxxxxxxxxxx.dkr.ecr.xxxxxxxxxxxxxxx.amazonaws.com \
-env CP_EDGE_AWS_ELB_SCHEME="internet-facing" \
-env CP_EDGE_AWS_ELB_SUBNETS="subnet-xxxxxxxxxxxxxxx" \
-env CP_EDGE_AWS_ELB_SG=",sg-xxxxxxxxxxxxxxx,sg-xxxxxxxxxxxxxxx" \
-env CP_EDGE_AWS_ELB_EIPALLOCS="eipalloc-xxxxxxxxxxxxxxx" \
-env CP_EDGE_KUBE_SERVICES_TYPE=elb
EOT

filesystem_mount = "fs-xxxxxxxxxxxxxxx.efs.xxxxxxxxxxxxxxx.amazonaws.com:/"
filesystem_type = "efs"
```

#### Outputs table of `cluster-infrastructure` module

| Name | Description |
|------|-------------|

| `filesystem_mount`  | Filesystem mount endpoint that can be used to mount ElasticFileSystem in EC2 JumpServer |
| `filesystem_type`   | Type of the created internet file system |
| `cp_pipectl_script` | Example of the pipeline install script with all necessary values from infrastructure
deployment |

> User can call terraform output again by run command:

```hcl
   terraform output <output name from table above>
```

> Note: You should deploy infrastructure first, to see output values.

## Cloud-pipeline deployment

1. Download latest pipectl binary file.
2. Mount created file system into instance.
    - For EFS run commands (https://docs.aws.amazon.com/efs/latest/ug/mounting-fs-mount-cmd-dns-name.html):
    ````
    fs_mount=$(terraform output -raw filesystem_mount)
    sudo yum install amazon-efs-utils -y 
    sudo mount -t efs -o tls $fs_mount  /opt
    ````

    - For FSx for Lustre (https://docs.aws.amazon.com/fsx/latest/LustreGuide/mounting-ec2-instance.html):
    ````
    fs_mount=$(terraform output -raw filesystem_mount)
    sudo amazon-linux-extras install -y lustre
    sudo mount -t lustre -o relatime,flock $fs_mount /opt
    ````

3. Create ssh key from `cluster-infrastructure` deployment:

```
sudo mkdir -p /opt/root/ssh

terraform show -json | jq -r ".values.root_module.child_modules[].resources[] |  select(.address==\"$(terraform state list | grep ssh_tls_key)\") |.values.private_key_pem" > /opt/root/ssh/ssh-key.pem
```

4. Take script from the `cluster-infrastructure` deployment [output](#output-of-cluster-infrastructure-module) and
   run it by using bash commands. For example:

```
CP_PIPECTL_URL=https://cloud-pipeline-oss-builds.s3.amazonaws.com/builds/<link-to-the-desired-pipectl-version> \

wget -c $CP_PIPECTL_URL -O pipectl && chmod +x pipectl \

terraform output -raw cp_pipectl_script > "deploy_cloud_pipeline.sh" && \

chmod +x deploy_cloud_pipeline.sh \

./deploy_cloud_pipeline.sh &> pipectl.log
```

5. Wait until deployment finishes.
6. Your Cloud-Pipeline environment should be available on the provided DNS name provided during
   deployment (`cp_api_srv_host`). 
