resource "aws_vpc" "cp_vpc_core" {
  cidr_block           = var.cp_vpc_cidr_core
  instance_tenancy     = "default"
  enable_dns_support   = "true" #gives you an internal domain name
  enable_dns_hostnames = "true" #gives you an internal host name
  tags = {
    Name = "${var.cp_region}-${var.cp_name_core}-${var.cp_env}-vpc"
  }
}

resource "aws_vpc" "cp_vpc_share" {
  cidr_block           = var.cp_vpc_cidr_share
  instance_tenancy     = "default"
  enable_dns_support   = "true" #gives you an internal domain name
  enable_dns_hostnames = "true" #gives you an internal host name 
  tags = {
    Name = "${var.cp_region}-${var.cp_name_share}-${var.cp_env}-vpc"
  }
}
