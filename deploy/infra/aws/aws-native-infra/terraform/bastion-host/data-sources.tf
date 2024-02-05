/*
===============================================================================
  Common
===============================================================================
*/
data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_region" "current" {}

data "aws_vpc" "this" {
  id = var.vpc_id
}

