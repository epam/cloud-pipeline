output "bastion_host_id" {
  description = "Id of created Bastion Host instance"
  value       = module.bastion_ec2_instance.id
}

output "output_message" {
  value = "Login to Bastion Host with command: aws ssm start-session --target ${module.bastion_ec2_instance.id} --region ${data.aws_region.current.name}"
}

