resource "aws_subnet" "cp_subnet_public_core" {
  vpc_id                  = aws_vpc.cp_vpc_core.id
  cidr_block              = var.cp_subnet_public_core
  map_public_ip_on_launch = "true"
  availability_zone       = data.aws_availability_zones.available.names[0]
  tags = {
    Name = "${var.cp_region}-${var.cp_name_core}-subnet-public"
  }
}

resource "aws_subnet" "cp_subnet_public_share" {
  vpc_id                  = aws_vpc.cp_vpc_share.id
  cidr_block              = var.cp_subnet_public_share
  map_public_ip_on_launch = "true"
  availability_zone       = data.aws_availability_zones.available.names[0]
  tags = {
    Name = "${var.cp_region}-${var.cp_name_share}-subnet-public"
  }
}

resource "aws_subnet" "cp_subnets_private_core" {
  for_each          = { for idx, az_name in local.az_names : idx => az_name }
  vpc_id            = aws_vpc.cp_vpc_core.id
  cidr_block        = cidrsubnet(var.cp_vpc_cidr_core, var.cp_subnet_private_rbits_core, each.key)
  availability_zone = local.az_names[each.key]

  tags = {
    Name = "${var.cp_region}-${var.cp_name_core}-${local.az_names[each.key]}-subnet-private"
  }
}

resource "aws_eip" "cp_eip_core" {
  instance = aws_instance.cp_core.id
  vpc      = true
  tags = {
    Name = "${var.cp_region}-${var.cp_name_core}-${var.cp_env}-eip"
  }
}

resource "aws_eip" "cp_eip_share" {
  instance = aws_instance.cp_share.id
  vpc      = true
  tags = {
    Name = "${var.cp_region}-${var.cp_name_share}-${var.cp_env}-eip"
  }
}

resource "aws_internet_gateway" "cp_igw_vpc_core" {
  vpc_id = aws_vpc.cp_vpc_core.id
}

resource "aws_internet_gateway" "cp_igw_vpc_share" {
  vpc_id = aws_vpc.cp_vpc_share.id
}

resource "aws_route_table" "cp_rt_public_core" {
  vpc_id = aws_vpc.cp_vpc_core.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.cp_igw_vpc_core.id
  }
}

resource "aws_route_table" "cp_rt_public_share" {
  vpc_id = aws_vpc.cp_vpc_share.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.cp_igw_vpc_share.id
  }
}

resource "aws_route_table_association" "cp_rta_core" {
  subnet_id      = aws_subnet.cp_subnet_public_core.id
  route_table_id = aws_route_table.cp_rt_public_core.id
}

resource "aws_route_table_association" "cp_rta_share" {
  subnet_id      = aws_subnet.cp_subnet_public_share.id
  route_table_id = aws_route_table.cp_rt_public_share.id
}

resource "aws_vpc_peering_connection" "cp_vpc_pc" {
  peer_vpc_id = aws_vpc.cp_vpc_core.id
  vpc_id      = aws_vpc.cp_vpc_share.id
  auto_accept = true

  tags = {
    Name = "VPC Peering between ${var.cp_name_core} and ${var.cp_name_share}"
  }
}

resource "aws_vpc_endpoint" "cp_s3_ep_core" {
  vpc_id       = aws_vpc.cp_vpc_core.id
  service_name = "com.amazonaws.${var.cp_region}.s3"

  tags = {
    Name = "${var.cp_region}-cloud-pipeline-${var.cp_env}-vpc"
  }
}

resource "aws_vpc_endpoint" "cp_s3_ep_share" {
  vpc_id       = aws_vpc.cp_vpc_share.id
  service_name = "com.amazonaws.${var.cp_region}.s3"

  tags = {
    Name = "${var.cp_region}-cloud-pipeline-share-${var.cp_env}-vpc"
  }
}

resource "aws_vpc_endpoint_route_table_association" "cp_vpc_ep_rta_core" {
  route_table_id  = aws_route_table.cp_rt_public_core.id
  vpc_endpoint_id = aws_vpc_endpoint.cp_s3_ep_core.id
}

resource "aws_vpc_endpoint_route_table_association" "cp_vpc_ep_rta_share" {
  route_table_id  = aws_route_table.cp_rt_public_share.id
  vpc_endpoint_id = aws_vpc_endpoint.cp_s3_ep_share.id
}
