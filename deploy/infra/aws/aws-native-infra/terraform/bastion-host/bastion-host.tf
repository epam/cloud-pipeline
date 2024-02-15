module "bastion_ec2_instance" {
  source = "terraform-aws-modules/ec2-instance/aws"
  name   = "${local.resource_name_prefix}-bastion"

  iam_instance_profile = var.iam_instance_profile == null ? aws_iam_instance_profile.bastion_execution[0].name : var.iam_instance_profile

  user_data_base64            = base64encode(local.user_data)
  user_data_replace_on_change = true

  ami           = var.jump_box_ami
  instance_type = var.jump_box_instance_type

  monitoring             = true
  vpc_security_group_ids = concat(var.additional_security_groups, [module.internal_bastion_access_sg.security_group_id])
  subnet_id              = var.subnet_id
  root_block_device = [
    {
      encrypted = true
    }
  ]

  metadata_options = {
    http_tokens = "required"
  }

  tags = local.tags
}

locals {
  user_data = <<-EOT
    #!/bin/bash

    echo "PATH=$PATH:/usr/local/bin" >> /etc/environment

    sudo yum remove awscli -y
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
    unzip awscliv2.zip && \
    sudo ./aws/install && \
    rm -rf awscliv2.zip ./aws

    sudo yum install git jq curl docker vim wget -y

    sudo wget https://releases.hashicorp.com/terraform/1.5.0/terraform_1.5.0_linux_amd64.zip
    sudo unzip terraform_1.5.0_linux_amd64.zip 
    chmod +x terraform
    sudo mv terraform /usr/local/bin/
    sudo rm terraform_1.5.0_linux_amd64.zip
    curl -LO https://dl.k8s.io/release/v1.28.2/bin/linux/amd64/kubectl
    sudo install -o root -g root -m 0755 kubectl /usr/bin/kubectl

    sudo systemctl enable docker.service
    sudo systemctl start docker.service
   
  EOT
}

module "internal_bastion_access_sg" {
  source              = "terraform-aws-modules/security-group/aws"
  version             = "5.1.0"
  vpc_id              = var.vpc_id
  name                = "${local.resource_name_prefix}-jumbox-internal-sg"
  ingress_cidr_blocks = [data.aws_vpc.this.cidr_block]
  ingress_rules       = ["all-all"]
  egress_cidr_blocks  = ["0.0.0.0/0"]
  egress_rules        = ["all-all"]
  tags                = local.tags
}
