resource "aws_instance" "cp_core" {
  ami           = data.aws_ami.latest-amazon2.id
  instance_type = var.cp_instance_type_core
  subnet_id     = aws_subnet.cp_subnet_public_core.id
  vpc_security_group_ids = [
    aws_security_group.cloud-pipeline-internal-cluster.id,
    aws_security_group.cloud-pipeline-https-access.id,
    aws_security_group.cloud-pipeline-ssh-access.id,
  aws_security_group.cloud-pipeline-share-core-access.id]
  key_name             = aws_key_pair.cp_ssh_key.key_name
  iam_instance_profile = aws_iam_instance_profile.cp-service-profile.name

  root_block_device {
    volume_size = var.cp_instance_storage_size_core
    volume_type = "gp2"
    encrypted   = true
    kms_key_id  = aws_kms_key.cp_kms_key.arn
  }

  user_data = templatefile("./templates/lustre.tmpl", {
    lustre_url = "${aws_fsx_lustre_file_system.cp_fsx_lustre.dns_name}@tcp:/${aws_fsx_lustre_file_system.cp_fsx_lustre.mount_name}"
  })

  tags = {
    Name = "cp-core"
  }
}

resource "aws_instance" "cp_share" {
  ami                    = data.aws_ami.latest-amazon2.id
  instance_type          = var.cp_instance_type_share
  subnet_id              = aws_subnet.cp_subnet_public_share.id
  vpc_security_group_ids = [aws_security_group.cloud-pipeline-share-internal-cluster.id, aws_security_group.cloud-pipeline-share-https-access.id, aws_security_group.cloud-pipeline-core-share-access.id]
  key_name               = aws_key_pair.cp_ssh_key.key_name

  root_block_device {
    volume_size = var.cp_instance_storage_size_share
    volume_type = "gp2"
    encrypted   = true
    kms_key_id  = aws_kms_key.cp_kms_key.arn
  }

  tags = {
    Name = "cp-share"
  }
}
