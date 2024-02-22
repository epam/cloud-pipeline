module "s3_etc" {
  source                   = "terraform-aws-modules/s3-bucket/aws"
  version                  = "3.15.1"
  bucket                   = "${local.resource_name_prefix}-etc"
  control_object_ownership = true
  object_ownership         = "BucketOwnerEnforced"


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
