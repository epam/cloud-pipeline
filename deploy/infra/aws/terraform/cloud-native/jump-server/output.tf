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

output "jump_sever_id" {
  description = "Id of created Jump Server instance"
  value       = module.ec2_instance.id
}

output "output_message" {
  value = "Login to Jump Server with command: aws ssm start-session --target ${module.ec2_instance.id} --region ${data.aws_region.current.name}"
}

output "jump_server_role" {
  description = "ARN of bastion execution role that must be set in EKS deployment module"
  value = aws_iam_role.bastion_execution[0].arn
}

