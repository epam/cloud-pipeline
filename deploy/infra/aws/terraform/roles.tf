resource "aws_iam_role" "cp_role_service" {
  name = "${var.cp_project}-Service"

  # Terraform's "jsonencode" function converts a
  # Terraform expression result to valid JSON syntax.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      },
    ]
  })

  tags = {
    Name = "${var.cp_project}-Service"
  }
}

resource "aws_iam_policy" "cp_policy_service" {
  name        = "${var.cp_project}-Service-Policy"
  description = "Cloud-Pipeline Service Policy"
  policy      = data.template_file.cp_policy_service_template.rendered

  tags = {
    Name = "${var.cp_project}-Service-Policy"
  }
}

resource "aws_iam_policy_attachment" "cp_policy_attach_service" {
  name       = "cp_policy_attach_service"
  roles      = ["${aws_iam_role.cp_role_service.name}"]
  policy_arn = aws_iam_policy.cp_policy_service.arn
}

resource "aws_iam_instance_profile" "cp-service-profile" {
  name = "${var.cp_project}-service-profile"
  role = aws_iam_role.cp_role_service.name
}

resource "aws_iam_role" "cp_role_s3viasts" {
  name               = "${var.cp_project}-S3viaSTS"
  assume_role_policy = <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "arn:aws:iam::${var.cp_account_id}:role/${var.cp_project}-Service"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
EOF
  depends_on         = [aws_iam_role.cp_role_service]
  tags = {
    Name = "${var.cp_project}-S3viaSTS"
  }
}

resource "aws_iam_policy" "cp_policy_s3viasts" {
  name        = "${var.cp_project}-S3viaSTS-Policy"
  description = "Cloud-Pipeline Service Policy"
  policy      = file("./policies/cp-policy-s3viasts.json")

  tags = {
    Name = "${var.cp_project}-S3viaSTS-Policy"
  }
}

resource "aws_iam_policy" "cp_policy_nicedcv" {
  name        = "${var.cp_project}-NiceDCV-Policy"
  description = "Cloud-Pipeline NiceDCV Policy"
  policy      = file("./policies/cp-policy-nicedcv.json")

  tags = {
    Name = "${var.cp_project}-NiceDCV-Policy"
  }
}

resource "aws_iam_policy_attachment" "cp_policy_attach_s3viasts" {
  name       = "cp_policy_attach_s3viasts"
  roles      = ["${aws_iam_role.cp_role_s3viasts.name}"]
  policy_arn = aws_iam_policy.cp_policy_s3viasts.arn
}

resource "aws_iam_instance_profile" "cp-s3viasts-profile" {
  name = "${var.cp_project}-s3viasts-profile"
  role = aws_iam_role.cp_role_s3viasts.name
}

resource "aws_kms_key" "cp_kms_key" {
  description = "${var.cp_project} KMS encryption key"
  key_usage   = "ENCRYPT_DECRYPT"
  is_enabled  = true
  policy      = data.template_file.cp_kms_key_template.rendered
  depends_on  = [aws_iam_role.cp_role_service]

  tags = {
    Name = "${var.cp_project}-KMS-${var.cp_region}"
  }
}

# The "aws_iam_service_linked_role" cannot be determined until apply, 
# so Terraform cannot predict whether these roles are being used in the organisation. 
# To work with this, first apply all resources, after that, uncomment the resources below if you need them.

/*resource "aws_iam_service_linked_role" "AWSServiceRoleForEC2Spot" {
  aws_service_name = "spot.amazonaws.com"
}

resource "aws_iam_service_linked_role" "AWSServiceRoleForAmazonFSx" {
  aws_service_name = "fsx.amazonaws.com"
}*/
