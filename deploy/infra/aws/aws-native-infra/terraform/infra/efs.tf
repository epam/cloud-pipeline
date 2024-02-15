module "cp_system_efs" {
  source  = "terraform-aws-modules/efs/aws"
  version = "1.3.1"

  # File system
  name           = local.efs_name
  creation_token = local.efs_name
  encrypted      = true

  performance_mode                = var.efs_performance_mode
  throughput_mode                 = var.efs_throughput_mode
  provisioned_throughput_in_mibps = var.efs_throughput_mode == "provisioned" ? var.efs_provisioned_throughput_in_mibps : null

  # File system policy
  attach_policy = false

  # Mount targets / security group
  mount_targets              = { for snet_id in data.aws_subnets.this.ids : snet_id => { subnet_id = snet_id } }

  security_group_description = "${local.resource_name_prefix} EFS security group"
  security_group_vpc_id      = var.vpc_id
  security_group_rules = {
    vpc = {
      # relying on the defaults provdied for EFS/NFS (2049/TCP + ingress)
      description = "NFS ingress from VPC"
      cidr_blocks = [data.aws_vpc.this.cidr_block]
    }
  }

  # Backup policy
  enable_backup_policy = true

  tags = local.tags
}