output "cp_eip_ip_core" {
  value = aws_eip.cp_eip_core.public_ip
}

output "cp_eip_ip_share" {
  value = aws_eip.cp_eip_share.public_ip
}

output "cp_eip_dns_core" {
  value = aws_eip.cp_eip_core.public_dns
}

output "cp_eip_dns_share" {
  value = aws_eip.cp_eip_share.public_dns
}

output "cp_kms_key" {
  value = aws_kms_key.cp_kms_key.arn
}

output "aws_instance_security_groups_core" {
  value = aws_instance.cp_core.vpc_security_group_ids
}

output "aws_availability_zones" {
  value = data.aws_availability_zones.available.names
}

output "cp_efs_dns_name" {
  value = aws_efs_file_system.cp_efs.dns_name
}
# The "for_each" value depends on resource attributes that cannot be determined until apply, 
# so Terraform cannot predict how many instances will be created. 
# To work with this, first apply resources that the for_each depends on.
# After that, uncomment the resources below if you need them.

/*output "aws_subnets_private_ids_core" {
  value = [for s in data.aws_subnet.cp_subnets_private_core : s.id]
}

output "aws_subnets_private_cidr_core" {
  value = [for s in data.aws_subnet.cp_subnets_private_core : s.cidr_block]
}*/
