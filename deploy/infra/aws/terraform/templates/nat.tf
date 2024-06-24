resource "aws_eip" "cp_natgw_eip_core" {
  vpc      = true
  
  tags = {
    Name = "${var.cp_region}-${var.cp_name_core}-${var.cp_env}-natgw-eip"
    }
}

resource "aws_nat_gateway" "cp_nat_core" {
  allocation_id = aws_eip.cp_natgw_eip_core.id
  subnet_id     = aws_subnet.cp_subnet_public_core.id
  
  tags = {
    Name = "${var.cp_region}-${var.cp_name_core}-${var.cp_env}-natgw"
    }
}

resource "aws_route_table" "cp_rt_natgw_core" {
  for_each      = aws_subnet.cp_subnets_private_core
  vpc_id = aws_vpc.cp_vpc_core.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.cp_nat_core.id
  }
}

resource "aws_route_table_association" "cp_rta_natgw_core" {
  for_each      = aws_subnet.cp_subnets_private_core
  subnet_id = aws_subnet.cp_subnets_private_core[each.key].id
  route_table_id = aws_route_table.cp_rt_natgw_core[each.key].id
}
