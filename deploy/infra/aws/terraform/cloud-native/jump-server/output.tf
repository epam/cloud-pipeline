output "jump_sever_id" {
  description = "Id of created Jump Server instance"
  value       = module.ec2_instance.id
}

output "output_message" {
  value = "Login to Jump Server with command: aws ssm start-session --target ${module.ec2_instance.id} --region ${data.aws_region.current.name}"
}

