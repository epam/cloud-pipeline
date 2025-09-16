# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

resource "aws_secretsmanager_secret" "ecr_dockerhub_secret" {
  count                   = var.ecr_sys_dockerhub_token != null ? 1 : 0
  name                    = "ecr-pullthroughcache/${local.resource_name_prefix}_dockerhub"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "ecr_dockerhub_secret" {
  count         = var.ecr_sys_dockerhub_token != null ? 1 : 0
  secret_id     = aws_secretsmanager_secret.ecr_dockerhub_secret[0].id
  secret_string = jsonencode({
    "username": var.ecr_sys_dockerhub_username,
    "accessToken": var.ecr_sys_dockerhub_token
  })
}

resource "aws_ecr_pull_through_cache_rule" "ecr_public" {
  ecr_repository_prefix      = "ecr-public"
  upstream_registry_url      = "public.ecr.aws"
  upstream_repository_prefix = "ROOT"
}

resource "aws_ecr_pull_through_cache_rule" "ecr_dockerhub" {
  count                      = var.ecr_sys_dockerhub_token != null ? 1 : 0
  ecr_repository_prefix      = "dockerhub"
  upstream_registry_url      = "registry-1.docker.io"
  upstream_repository_prefix = "ROOT"
  credential_arn             = aws_secretsmanager_secret.ecr_dockerhub_secret[0].arn

  depends_on = [aws_secretsmanager_secret.ecr_dockerhub_secret, aws_secretsmanager_secret_version.ecr_dockerhub_secret]
}


