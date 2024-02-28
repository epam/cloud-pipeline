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
  default     = "t3.micro"
  description = "Instance Type that will be used"
}

variable "vpc_id" {
  type        = string
  description = "Id of the VCP to be used for deployment of the bastion instance"
}

variable "ami_id" {
  default     = ""
  description = "AMI to be used for bastion ec2 instance. If empty - eks-optimized will be used."
}

variable "ami_name_filter" {
  description = "AMI name to be used as filter in data source to find an AMI id."
  default = "amazon-eks-node-1.28-v20240213"
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
