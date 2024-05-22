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

resource "aws_cloudwatch_log_group" "cw_performance" {
  name              = "/aws/containerinsights/${module.eks.cluster_name}/performance"
  kms_key_id        = module.kms_eks.key_arn
  retention_in_days = var.eks_cloudwatch_logs_retention_in_days
}

resource "aws_cloudwatch_log_group" "cw_application" {
  name              = "/aws/containerinsights/${module.eks.cluster_name}/application"
  kms_key_id        = module.kms_eks.key_arn
  retention_in_days = var.eks_cloudwatch_logs_retention_in_days
}

resource "aws_cloudwatch_log_group" "cw_dataplane" {
  name              = "/aws/containerinsights/${module.eks.cluster_name}/dataplane"
  kms_key_id        = module.kms_eks.key_arn
  retention_in_days = var.eks_cloudwatch_logs_retention_in_days
}

resource "aws_cloudwatch_log_group" "cw_host" {
  name              = "/aws/containerinsights/${module.eks.cluster_name}/host"
  kms_key_id        = module.kms_eks.key_arn
  retention_in_days = var.eks_cloudwatch_logs_retention_in_days
}
