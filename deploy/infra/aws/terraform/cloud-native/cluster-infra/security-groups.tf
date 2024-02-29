module "internal_cluster_access_sg" {
  source              = "terraform-aws-modules/security-group/aws"
  version             = "5.1.0"
  vpc_id              = data.aws_vpc.this.id
  name                = "${local.resource_name_prefix}-internal-access-sg"
  ingress_cidr_blocks = [data.aws_vpc.this.cidr_block]
  ingress_rules       = ["all-all"]
  egress_cidr_blocks  = ["0.0.0.0/0"]
  egress_rules        = ["all-all"]
  tags                = local.tags
}

module "https_access_sg" {
  source                  = "terraform-aws-modules/security-group/aws"
  version                 = "5.1.0"
  vpc_id                  = data.aws_vpc.this.id
  name                    = "${local.resource_name_prefix}-https-access-sg"
  ingress_prefix_list_ids = var.cp_api_access_prefix_lists
  ingress_with_prefix_list_ids = [
    {
      from_port = 443
      to_port   = 443
      protocol  = "tcp"
    },
  ]
  tags = local.tags
}

