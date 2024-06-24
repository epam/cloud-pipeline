resource "tls_private_key" "cp_rsa_key" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "cp_ssh_key" {
  key_name   = var.cp_ssh_key
  public_key = tls_private_key.cp_rsa_key.public_key_openssh

  provisioner "local-exec" { # Key *.pem will be create in current directory
    command = "echo '${tls_private_key.cp_rsa_key.private_key_pem}' > ./'${var.cp_ssh_key}'.pem"
  }

  provisioner "local-exec" {
    command = "chmod 400 ./'${var.cp_ssh_key}'.pem"
  }
}

resource "aws_security_group" "cloud-pipeline-internal-cluster" {
  name        = "cloud-pipeline-internal-cluster"
  description = "Allow internal cluster traffic"
  vpc_id      = aws_vpc.cp_vpc_core.id

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cloud-pipeline-internal-cluster"
  }
}

resource "aws_security_group" "cloud-pipeline-https-access" {
  name        = "cloud-pipeline-https-access"
  description = "Allow HTTPS traffic from client network"
  vpc_id      = aws_vpc.cp_vpc_core.id

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    #prefix_list_ids = ["aws_ec2_managed_prefix_list.epm_pl.prefix_list_id"]
  }

  tags = {
    Name = "cloud-pipeline-https-access"
  }
}

resource "aws_security_group" "cloud-pipeline-ssh-access" {
  name        = "cloud-pipeline-ssh-access"
  description = "Allow SSH traffic from client network"
  vpc_id      = aws_vpc.cp_vpc_core.id

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    #prefix_list_ids = ["aws_ec2_managed_prefix_list.epm_pl.prefix_list_id"]
  }

  tags = {
    Name = "cloud-pipeline-ssh-access"
  }
}

resource "aws_security_group" "cloud-pipeline-share-core-access" {
  name        = "cloud-pipeline-share-core-access"
  description = "Allow all traffic from Share service to Core"
  vpc_id      = aws_vpc.cp_vpc_core.id

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.cp_subnet_public_share]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.cp_subnet_public_share]
  }

  tags = {
    Name = "cloud-pipeline-share-core-access"
  }
}

resource "aws_security_group" "cloud-pipeline-share-internal-cluster" {
  name        = "cloud-pipeline-share-internal-cluster"
  description = "Allow internal Share traffic"
  vpc_id      = aws_vpc.cp_vpc_share.id

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cloud-pipeline-share-internal-cluster"
  }
}

resource "aws_security_group" "cloud-pipeline-share-https-access" {
  name        = "cloud-pipeline-share-https-access"
  description = "Allow HTTPS traffic from client network to Share service"
  vpc_id      = aws_vpc.cp_vpc_share.id

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cloud-pipeline-share-https-access"
  }
}

resource "aws_security_group" "cloud-pipeline-core-share-access" {
  name        = "cloud-pipeline-core-share-access"
  description = "Allow all traffic from Core service to Share"
  vpc_id      = aws_vpc.cp_vpc_share.id

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.cp_subnet_public_core]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = [var.cp_subnet_public_core]
  }

  tags = {
    Name = "cloud-pipeline-core-share-access"
  }
}

resource "aws_ec2_managed_prefix_list" "epm_eu_pl" {
  name           = "epm-pl"
  address_family = "IPv4"
  max_entries    = 60

  /* entry {
    cidr        = aws_vpc.example.cidr_block
    description = "Primary"
  }

  entry {
    cidr        = aws_vpc_ipv4_cidr_block_association.example.cidr_block
    description = "Secondary"
  }

  tags = {
    Env = "live"
  } */
}
