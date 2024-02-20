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
  description = "Id of the VCP to be used for Compute Layer"
}

variable "subnet_ids" {
  type        = list(string)
  description = "Ids of the VCP subnets to be used for Compute Layer EKS cluster"
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
  default = "1.28"
}

variable "eks_system_node_group_instance_type" {
  type        = string
  default     = "m5.large"
  description = "Node instance type for system ng, which will host autoscaler, fsx-csi controller, etc."
}

variable "eks_system_node_group_size" {
  type        = number
  default     = 1
  description = "Number of nodes o spin up for internal-system eks node group. (Nodes that will host internal workload such as fsx-csi plugin, autoscaler, etc)"
}

variable "eks_system_node_group_volume_size" {
  type        = number
  default     = 200
  description = "Volume size of cp-system EKS node."
}
variable "eks_system_node_group_volume_type" {
  default     = "gp3"
  type        = string
  description = "Volume type of cp-system EKS node."
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

variable "eks_cloudwatch_logs_retention_in_days" {
  type        = number
  default     = 30
  description = "Possible values are: 1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653, and 0. If you select 0, the events in the log group are always retained and never expire."
}
