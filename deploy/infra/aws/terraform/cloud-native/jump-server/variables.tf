variable "env" {
  type        = string
  description = "Environment name"
}

variable "project_name" {
  type        = string
  description = "Project name"
}

variable "subnet_id" {
  type        = string
  description = "Id of the VCP subnet to be used to launch an instance"
}

variable "iam_instance_profile" {
  type        = string
  default     = null
  description = "EC2 instance profile name"
}

variable "instance_type" {
  default     = "t3.small"
  description = "Instance Type that will be used"
}

variable "ebs_volume_size" {
  type    = number
  default = 200
  description = "Size of the ebs volume to attach to the jump-server"
}

variable "vpc_id" {
  type        = string
  description = "Id of the VCP to be used for deployment of the jump server"
}

variable "ami_id" {
  default     = ""
  description = "AMI to be used for jump ec2 instance. If empty - eks-optimized will be used."
}

variable "ami_name_filter" {
  description = "AMI name to be used as filter in data source to find an AMI id."
  default     = "amazon-eks-node-1.29-v20240227"
}

variable "iam_role_permissions_boundary_arn" {
  type        = string
  default     = null
  description = "Account specific role boundaries"
}

variable "additional_security_groups" {
  type        = list(string)
  default     = []
  description = "List of SG's IDs to add to the instance."
}

variable "additional_tags" {
  type        = map(string)
  default     = {}
  description = "Additional tags for the infrastructure tagging"
}
