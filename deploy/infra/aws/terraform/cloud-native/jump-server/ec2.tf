# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

module "ec2_instance" {
  source = "terraform-aws-modules/ec2-instance/aws"
  name   = "${local.resource_name_prefix}-jump-server"

  iam_instance_profile = var.iam_instance_profile == null ? aws_iam_instance_profile.bastion_execution[0].name : var.iam_instance_profile

  user_data_base64            = base64encode(local.user_data)
  user_data_replace_on_change = true

  ami           = var.ami_id != "" ? var.ami_id : data.aws_ami.eks_ami.id
  instance_type = var.instance_type

  metadata_options = {
    "http_endpoint" : "enabled",
    "http_put_response_hop_limit" : 1,
    "http_tokens" : "optional"
  }

  monitoring             = true
  vpc_security_group_ids = concat(var.additional_security_groups, [module.internal_bastion_access_sg.security_group_id])
  subnet_id              = var.subnet_id
  root_block_device      = [
    {
      volume_size = var.ebs_volume_size
      encrypted   = true
    }
  ]

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

    sudo yum install git jq curl vim wget -y

    sudo wget https://releases.hashicorp.com/terraform/1.5.0/terraform_1.5.0_linux_amd64.zip
    sudo unzip terraform_1.5.0_linux_amd64.zip 
    chmod +x terraform
    sudo mv terraform /usr/local/bin/
    sudo rm terraform_1.5.0_linux_amd64.zip

    curl -LO https://dl.k8s.io/release/v1.29.2/bin/linux/amd64/kubectl
    sudo install -o root -g root -m 0755 kubectl /usr/bin/kubectl

    sudo yum install -y docker
    sudo systemctl enable docker
    sudo systemctl start docker

  EOT
}

module "internal_bastion_access_sg" {
  source              = "terraform-aws-modules/security-group/aws"
  version             = "5.1.0"
  vpc_id              = var.vpc_id
  name                = "${local.resource_name_prefix}-jump-server-internal-sg"
  ingress_cidr_blocks = [data.aws_vpc.this.cidr_block]
  ingress_rules       = ["all-all"]
  egress_cidr_blocks  = ["0.0.0.0/0"]
  egress_rules        = ["all-all"]
  tags                = local.tags
}
