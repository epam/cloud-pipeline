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

variable "deployment_env" {
  type        = string
  description = "Environment name. Will be used as resources name prefix"
}

variable "deployment_name" {
  type        = string
  description = "Name of the deployment. Will be used as resources name prefix"
}

variable "vpc_id" {
  type        = string
  description = "Id of the VCP to be used for Cloud Pipeline"
}

variable "subnet_ids" {
  type        = list(string)
  description = "Ids of the VCP subnets to be used for Cloud Pipeline EKS cluster, FS mount points, etc."

  validation {
    condition     = length(var.subnet_ids) > 0
    error_message = "At least one subnet id in list must be specified"
  }
}

variable "iam_role_permissions_boundary_arn" {
  type        = string
  default     = null
  description = "Account specific role boundaries"
}

variable "additional_tags" {
  type        = map(string)
  default     = {}
  description = "Additional tags for the infrastructure tagging"
}

variable "eks_cluster_version" {
  type    = string
  default = "1.29"
}

variable "eks_system_node_group_subnet_ids" {
  type        = list(string)
  description = "Ids of the VCP subnets to be used for EKS cluster Cloud Pipeline system node group."

  validation {
    condition     = length(var.eks_system_node_group_subnet_ids) > 0
    error_message = "At least one subnet id in list must be specified"
  }
}

variable "eks_system_node_group_instance_type" {
  type        = string
  default     = "m5.xlarge"
  description = "Node instance type for system ng, which will host Cloud-Pipeline services."
}

variable "eks_system_node_group_size" {
  type        = number
  default     = 1
  description = "Number of nodes to spin up for Cloud-Pipeline system EKS node group."
}

variable "eks_system_node_group_volume_size" {
  type        = number
  default     = 200
  description = "Volume size of Cloud-Pipeline system EKS node."
}
variable "eks_system_node_group_volume_type" {
  default     = "gp3"
  type        = string
  description = "Volume type of Cloud-Pipeline system EKS node."
}

variable "eks_additional_role_mapping" {
  type = list(object({
    iam_role_arn  = string
    eks_role_name = string
    eks_groups    = list(string)
  }))
  default     = []
  description = "List of additional roles mapping for aws_auth map."
}


variable "eks_additional_user_mapping" {
  type = list(object({
    iam_user_arn  = string
    eks_role_name = string
    eks_groups    = list(string)
  }))
  default     = []
  description = "List of IAM user mapping for aws_auth map."
}

variable "eks_cloudwatch_logs_retention_in_days" {
  type        = number
  default     = 30
  description = "Possible values are: 1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653, and 0. If you select 0, the events in the log group are always retained and never expire."
}

variable "eks_vpc_cni_driver_custom_config" {
  default = {
    enable          = true,
    min_ip_target   = 5,
    warm_ip_target  = 2,
    warm_eni_target = 0
  }
  type = object({
    enable          = bool,
    min_ip_target   = number,
    warm_ip_target  = number,
    warm_eni_target = number
  })
}

###################################################################
#                  File systems for Cloud-Pipeline deployment
###################################################################

variable "deploy_filesystem_type" {
  type        = string
  default     = "efs"
  description = "Option to create EFS or FSx Lustre filesystem: must be set efs or fsx. If empty, no FS will be created."
  validation {
    condition     = contains(["efs", "fsx"], var.deploy_filesystem_type)
    error_message = "The value of the deploy_filesystem_type variable can be only efs or fsx or null. Please check that variable is set correctly."
  }
}

variable "efs_performance_mode" {
  type        = string
  default     = "generalPurpose"
  description = "EFS performance mode, can be 'generalPurpose' or 'maxIO'"
}

variable "efs_throughput_mode" {
  type        = string
  default     = "elastic"
  description = "EFS throughput mode, valid values: 'bursting', 'provisioned', or 'elastic'"
}

variable "efs_provisioned_throughput_in_mibps" {
  type        = number
  default     = null
  description = "EFS throughput, measured in MiB/s"
}

variable "fsx_storage_capacity" {
  type        = number
  default     = 1200
  description = "FSx Lustre storage capacity in MB"
}

variable "fsx_deployment_type" {
  type        = string
  default     = "PERSISTENT_1"
  description = "The filesystem deployment type. One of: SCRATCH_1, SCRATCH_2, PERSISTENT_1, PERSISTENT_2"
}

variable "fsx_per_unit_storage_throughput" {
  type        = number
  default     = 200
  description = "Describes the amount of read and write throughput for each 1 tebibyte of storage, in MB/s/TiB, required for the PERSISTENT_1 and PERSISTENT_2 deployment_type"
}

variable "external_access_security_group_ids" {
  type        = list(string)
  default     = []
  description = "List of SG's IDs to attach to Cloud-Pipeline's ELB. Could be useful f.i., to provide access to the system."
}

variable "create_ssh_rsa_key_pair" {
  default     = true
  description = "If true, this module will create ssh_rsa key pair in the AWS account. This pair can be used during Cloud-Pipeline deployment process as a ssh key for worker nodes."
}

variable "enable_aws_omics_integration" {
  default     = true
  description = "If true, this module will create several resource to be used to integrate AWS Omics with Cloud-Pipeline (such as ECR, Omics Service Role)."
}

###################################################################
#                  AWS RDS for Cloud-Pipeline deployment
###################################################################
variable "deploy_rds" {
  type        = bool
  default     = true
  description = "Option to create RDS instance or not"
}

variable "create_cloud_pipeline_db_configuration" {
  type        = bool
  default     = true
  description = "Option to create additional database or not"
}

variable "rds_instance_type" {
  type        = string
  default     = "db.m6i.large"
  description = "The instance type of the RDS instance"
}

variable "rds_storage_size" {
  type        = number
  default     = 200
  description = "The allocated RDS storage size in gigabytes"
}

variable "rds_default_db_name" {
  type        = string
  default     = "postgres"
  description = "The DB name to create. If omitted, no database is created initially"
}

variable "rds_root_username" {
  type        = string
  default     = "postgres"
  description = "Username for the master DB user"
}

variable "rds_root_password" {
  type        = string
  default     = null
  description = "Password for the default master DB user"
}

variable "cloud_pipeline_db_configuration" {
  type = map(object({
    username = string
    password = string
    database = string
  }))
  default = {
    pipeline = {
      username = "pipeline"
      password = "pipeline"
      database = "pipeline"
    }
    clair = {
      username = "clair"
      password = "clair"
      database = "clair"
    }
  }
  description = "Username with password and database, which will be created by Postgres provider. Username will be owner of the database."
}

variable "rds_db_port" {
  type        = number
  default     = 5432
  description = "The port on which the RDS instance accepts connections"
}

variable "rds_force_ssl" {
  type        = number
  default     = 1
  description = "Forces clients to connect to the RDS with SSL."
}

###################################################################
# Cloud-pipeline deployment script specific variables
# Such variable doesn't effect infrastructure,
# but define how Cloud-Pipeline will be deployed on top of this infrastructure
###################################################################

variable "cp_deployment_id" {
  type        = string
  default = "Cloud-Pipeline"
  description = "Deployment Name"
}

variable "cp_edge_elb_schema" {
  type        = string
  default     = "internet-facing"
  description = "User-facing ELB schema. Possible values 'internal' or 'internet-facing'."
}

variable "cp_edge_elb_subnet" {
  type        = string
  description = "Subnet id to deploy user-facing AWS Elastic Load Balancer."
}

variable "cp_edge_elb_ip" {
  type        = string
  description = "Allocation ID of the pre-created Elastic IP. Will be used to assign to the user-facing ELB."
}

variable "cp_api_srv_host" {
  type        = string
  description = "API SRV service domain name address"
}

variable "cp_idp_host" {
  type        = string
  default     = null
  description = "Cloud-Pipeline Self hosted IDP service domain name address"
}

variable "cp_docker_host" {
  type        = string
  description = "Docker Registry service domain name address"
}

variable "cp_edge_host" {
  type        = string
  description = "EDGE service domain name address"
}

variable "cp_gitlab_host" {
  type        = string
  description = "GITLAB service domain name address"
}

variable "cp_default_admin_name" {
  type        = string
  default     = null
  description = "Default administrator of the Cloud-Pipeline"
}

variable "cp_api_srv_saml_user_attr" {
  type        = string
  description = "Option CP_API_SRV_SAML_USER_ATTRIBUTES for use with external IDP service. Default for Azure AD."
  default     = "Email=http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress,FirstName=http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname,LastName=http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname)"
}
