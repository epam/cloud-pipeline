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

resource "tls_private_key" "ssh_tls_key" {
  algorithm = "RSA"
}

module "ssh_rsa_key_pair" {
  source  = "terraform-aws-modules/key-pair/aws"
  version = "2.0.2"

  create     = var.create_ssh_rsa_key_pair
  key_name   = "${local.resource_name_prefix}-key"
  public_key = trimspace(tls_private_key.ssh_tls_key.public_key_openssh)
}
