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

