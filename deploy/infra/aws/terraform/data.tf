data "aws_availability_zones" "available" {}

data "aws_availability_zone" "available" {
  for_each = toset(data.aws_availability_zones.available.names)
  name     = each.value
}

data "aws_availability_zones" "azs" {
  state = "available"
}

locals {
  az_names = data.aws_availability_zones.azs.names
}

locals {
  az_map = {
    for zone in data.aws_availability_zone.available :
    zone.name => zone.zone_id
  }
}

data "aws_ami" "latest-amazon2" {
  owners      = ["amazon"]
  most_recent = true
  filter {
    name   = "name"
    values = ["amzn2-ami-hvm-*-x86_64-gp2"]
  }
}

data "template_file" "cp_policy_service_template" {
  template = file("./policies/cp-policy-service.json")
  vars = {
    cp_account_id = var.cp_account_id
    cp_project    = var.cp_project
    cp_region     = var.cp_region
  }
}

data "template_file" "cp_policy_nicedcv_template" {
  template = file("./policies/cp-policy-nicedcv.json")
  vars = {
    cp_region = var.cp_region
  }
}

data "template_file" "cp_kms_key_template" {
  template = file("./policies/cp-kms-key.json")
  vars = {
    cp_account_id = var.cp_account_id
    cp_project    = var.cp_project
    cp_region     = var.cp_region
  }
}

data "aws_subnets" "cp_subnets_private_core" {
  filter {
    name   = "vpc-id"
    values = [aws_vpc.cp_vpc_core.id]
  }
}

# The "for_each" value depends on resource attributes that cannot be determined until apply, 
# so Terraform cannot predict how many instances will be created. 
# To work with this, first apply resources that the for_each depends on.
# After that, uncomment the resources below if you need them.

/*data "aws_subnet" "cp_subnets_private_core" {
  for_each = toset(data.aws_subnets.cp_subnets_private_core.ids)
  id       = each.value
}*/
