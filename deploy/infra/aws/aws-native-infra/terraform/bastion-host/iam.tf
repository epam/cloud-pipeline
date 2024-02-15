resource "aws_iam_role" "bastion_execution" {
  name                 = "${local.resource_name_prefix}BastionExecutionRole"
  count                = var.iam_instance_profile == null ? 1 : 0
  permissions_boundary = var.iam_role_permissions_boundary_arn

  assume_role_policy = jsonencode({

    "Version" : "2012-10-17",
    "Statement" : [
      {
        "Effect" : "Allow",
        "Principal" : {
          "Service" : [
            "ec2.amazonaws.com"
          ]
        },
        "Action" : "sts:AssumeRole"
      }
    ]

  })
}

data "aws_iam_policy" "AmazonSSMManagedInstanceCore" {
  arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}


resource "aws_iam_role_policy_attachment" "bastion_execution" {
  count = var.iam_instance_profile == null ? 1 : 0

  role       = aws_iam_role.bastion_execution[0].name
  policy_arn = data.aws_iam_policy.AmazonSSMManagedInstanceCore.arn
}

resource "aws_iam_instance_profile" "bastion_execution" {
  count = var.iam_instance_profile == null ? 1 : 0

  name = "${aws_iam_role.bastion_execution[0].name}_ip"
  role = aws_iam_role.bastion_execution[0].name
}