module "internal_cluster_access_sg" {
  source            = "terraform-aws-modules/security-group/aws"
  version           = "5.1.0"
  vpc_id            = data.aws_vpc.this.id
  name              = "${local.resource_name_prefix}-internal-access-sg"
  ingress_cidr_blocks = [data.aws_vpc.this.cidr_block]
  ingress_rules       = ["all-all"]
  egress_cidr_blocks = ["0.0.0.0/0"]
  egress_rules       = ["all-all"]
  tags               = local.tags
}

module "https_access_sg" {
  source                            = "terraform-aws-modules/security-group/aws"
  version                           = "5.1.0"
  vpc_id                            = data.aws_vpc.this.id
  name                              = "${local.resource_name_prefix}-https-access-sg"
  computed_ingress_with_cidr_blocks = [
    {
      rule        = "https-443-tcp",
      cidr_blocks = var.cp_api_access_cidr_blocks
    }
  ]
  tags = local.tags
}

