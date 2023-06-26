variable "cp_account_id" {
  description = "Please enter 12-digit AWS account ID. The ID is used in the body of policies."
}

variable "cp_region" {
  description = "AWS region for deployment. Infrastructure will be created in this region. That also used in the body of policies and to form tags for several cloud resources such as vpc, subnets, etc."
  default     = "us-west-2"
}

variable "cp_ssh_key" {
  type        = string
  default     = "cloud-pipeline-ssh-key"
  description = "Key-pair name. This key will be stored in AWS and used to have access to Core and Share instances."
}

variable "cp_project" {
  description = "The project name is substituted in the body of policies and service names."
  default     = "epam"
}

variable "cp_name_core" {
  description = "Core name suffix. That used to form tags for several cloud resources such as vpc, subnets, etc."
  default     = "cloud-pipeline-core"
}

variable "cp_name_share" {
  description = "Share name suffix. That used to form tags for several cloud resources such as vpc, subnets, etc."
  default     = "cloud-pipeline-share"
}

variable "cp_env" {
  description = "Enviromnent name. That used to form tags for several cloud resources such as vpc, subnets, etc."
  default     = "dev"
}

variable "cp_vpc_cidr_core" {
  description = "AWS VPC cidr block for Core area."
  default     = "10.0.0.0/16"
}

variable "cp_vpc_cidr_share" {
  description = "AWS VPC cidr block for Share area."
  default     = "10.20.0.0/16"
}

variable "cp_subnet_private_rbits_core" {
  description = "Relative bits. Used to calculate the size of the subnet. Plus a relative bit to the CIDR mask to get the mask size."
  default     = "6"
}

variable "cp_subnet_public_core" {
  description = "Public subnet for Core resources area. In this subnet cloud-pipeline Core instance will be lunched."
  default     = "10.0.90.0/24"
}

variable "cp_subnet_public_share" {
  description = "Public subnet for Share resources area. In this subnet cloud-pipeline Share instance will be lunched."
  default     = "10.20.90.0/24"
}

variable "cp_instance_private_ip_core" {
  description = "Core instance static private ip address"
  default     = "10.0.0.12"
}

variable "cp_instance_type_core" {
  description = "AWS EC2 instance type. This tipe will be used to launch an instance for Core."
  default     = "m5.xlarge"
}

variable "cp_instance_type_share" {
  description = "AWS EC2 instance type. This tipe will be used to launch an instance for Share."
  default     = "m5.xlarge"
}

variable "cp_instance_storage_size_core" {
  description = "AWS EC2 instance root storage size for Core in GiB."
  default     = "200"
}

variable "cp_instance_storage_size_share" {
  description = "AWS EC2 instance root storage size for Share in GiB."
  default     = "100"
}

variable "cp_fsx_lustre_size" {
  description = "AWS FSx for Lustre storage size in GiB. This file system will be mounted to Core instance and will store all necessary data like config files, logs, docker images, etc."
  default     = "1200"
}

variable "cp_fsx_lustre_throughput" {
  description = "Describes the amount of read and write throughput for each 1 tebibyte of storage, in MB/s/TiB, required for the PERSISTENT_1 and PERSISTENT_2 deployment_type."
  default     = "500"
}

variable "cp_efs_throughput" {
  description = "The throughput, measured in MiB/s, that you want to provision for the AWS EFS file system."
  default     = "500"
}

variable "cp_zone_id" {
  type        = string
  description = "The ID of the hosted zone to contain this record. Used to create A, CNAME records with the domain name in AWS Route53 required for Cloud-Pipeline deployment. Required if the customer does not have DNS zones for Cloud-Pipeline deployment."
  default     = ""
}

variable "cp_domain_name" {
  type        = string
  description = "The domain name of the hosted zone. Used to create A, CNAME records with the domain name in AWS Route53 required for Cloud-Pipeline deployment. Required if the customer does not have DNS zones for Cloud-Pipeline deployment."
  default     = "aws.cloud-pipeline.com"
}

variable "cp_domain_records" {
  type        = string
  description = "The name of third-level domain. Used to create A, CNAME records like as auth, docker, git with the domain name in AWS Route53 required for Cloud-Pipeline deployment. Required if the customer does not have DNS zones for Cloud-Pipeline deployment."
  default     = "test"
}
