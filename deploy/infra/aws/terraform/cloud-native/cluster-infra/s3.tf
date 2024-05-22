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

module "s3_etc" {
  source                   = "terraform-aws-modules/s3-bucket/aws"
  version                  = "3.15.1"
  bucket                   = "${local.resource_name_prefix}-etc"
  control_object_ownership = true
  object_ownership         = "BucketOwnerEnforced"
  force_destroy            = true


  server_side_encryption_configuration = {
    rule = {
      apply_server_side_encryption_by_default = {
        kms_master_key_id = module.kms.key_arn
        sse_algorithm     = "aws:kms"
      }
    }
  }

  tags = merge(
    {
      Type = "etc"
    },
    local.tags
  )

}

module "s3_docker" {
  source                   = "terraform-aws-modules/s3-bucket/aws"
  version                  = "3.15.1"
  bucket                   = "${local.resource_name_prefix}-docker"
  control_object_ownership = true
  object_ownership         = "BucketOwnerEnforced"
  force_destroy            = true


  lifecycle_rule = [
    {
      id      = "Clean Incomplete Multipart Uploads"
      enabled = true
      filter = {
        prefix = ""
      }
      abort_incomplete_multipart_upload_days = 5

    }
  ]

  server_side_encryption_configuration = {
    rule = {
      apply_server_side_encryption_by_default = {
        kms_master_key_id = module.kms.key_arn
        sse_algorithm     = "aws:kms"
      }
    }
  }

  tags = merge(
    {
      Type = "docker"
    },
    local.tags
  )

}
