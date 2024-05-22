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
  count = length(var.cp_api_access_prefix_lists) > 0 ? 1 : 0
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

