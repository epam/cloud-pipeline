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
