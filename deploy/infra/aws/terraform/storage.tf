resource "aws_fsx_lustre_file_system" "cp_fsx_lustre" {
  deployment_type             = "PERSISTENT_2"
  storage_capacity            = var.cp_fsx_lustre_size
  per_unit_storage_throughput = var.cp_fsx_lustre_throughput
  subnet_ids                  = [aws_subnet.cp_subnet_public_core.id]
  security_group_ids          = [aws_security_group.cloud-pipeline-internal-cluster.id]

  tags = {
    Name = "${var.cp_region}-cloud-pipeline-${var.cp_env}-lustre"
  }
}

resource "aws_efs_file_system" "cp_efs" {
  creation_token                  = "${var.cp_project}-efs"
  throughput_mode                 = "provisioned"
  provisioned_throughput_in_mibps = var.cp_efs_throughput
  encrypted                       = "true"
  kms_key_id                      = aws_kms_key.cp_kms_key.arn
  lifecycle {
    create_before_destroy = true
  }
  tags = {
    Name = "${var.cp_region}-cloud-pipeline-${var.cp_env}-efs"
  }
}

resource "aws_efs_mount_target" "cp_efs_mt" {
  file_system_id  = aws_efs_file_system.cp_efs.id
  for_each        = aws_subnet.cp_subnets_private_core
  subnet_id       = aws_subnet.cp_subnets_private_core[each.key].id
  security_groups = [aws_security_group.cloud-pipeline-internal-cluster.id]
}
