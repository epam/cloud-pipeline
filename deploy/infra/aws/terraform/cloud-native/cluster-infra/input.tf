variable "env" {
  type        = string
  description = "Environment name"
}

variable "project_name" {
  type        = string
  description = "Project name"
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

variable "cp_api_access_prefix_lists" {
  default     = []
  type        = list(string)
  description = "Prefix Lists to which access to Cloud Pipeline API will be granted."

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

variable "eks_cloudwatch_logs_retention_in_days" {
  type        = number
  default     = 30
  description = "Possible values are: 1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653, and 0. If you select 0, the events in the log group are always retained and never expire."
}

###################################################################
#                  File systems for Cloud-Pipeline deployment
###################################################################

variable "deploy_filesystem_type" {
  type        = string
  default     = "efs"
  description = "Option to create EFS or FSx Lustre filesystem: must be set efs or fsx. If empty, no FS will be created."
  validation {
    condition     = contains(["efs", "fsx", null], var.deploy_filesystem_type)
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

variable "additional_security_group_ids" {
  type        = list(string)
  default     = []
  description = "List of SG's IDs to attach to EKS cluster."
}

variable "create_ssh_rsa_key_pair" {
  default     = true
  description = "If true, this module will create ssh_rsa key pair in the AWS account. This pair can be used during Cloud-Pipeline deployment process as a ssh key for worker nodes."
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
  type = list(object({
    username = string
    password = string
    database = string
  }))
  default = [
    {
      username = "pipeline"
      password = "pipeline"
      database = "pipeline"
    },
    {
      username = "clair"
      password = "clair"
      database = "clair"
    }
  ]
  description = "Username with password and database, which will be created by Postgres provider. Username will be owner of the database."
}

variable "rds_db_port" {
  type        = number
  default     = 5432
  description = "The port on which the RDS instance accepts connections"
}
