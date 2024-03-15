resource "aws_ecr_repository" "cp_omics_ecr" {
  name  = "${lower(local.resource_name_prefix)}_aws_omics"
  count = var.enable_aws_omics_integration ? 1 : 0
  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = module.kms.key_arn
  }

  tags = local.tags
}

resource "aws_ecr_lifecycle_policy" "ecr_lifecycle_policy" {
  repository = aws_ecr_repository.cp_omics_ecr[0].name
  count      = var.enable_aws_omics_integration ? 1 : 0

  policy = <<EOF
{
    "rules": [
        {
            "rulePriority": 1,
            "description": "Expire untagged images older than 30 day(s)",
            "selection": {
                "tagStatus": "untagged",
                "countType": "sinceImagePushed",
                "countUnit": "days",
                "countNumber": 30
            },
            "action": {
                "type": "expire"
            }
        }
    ]
}
EOF
}