resource "tls_private_key" "ssh_tls_key" {
  algorithm = "RSA"
}

module "ssh_rsa_key_pair" {
  source  = "terraform-aws-modules/key-pair/aws"
  version = "2.0.2"

  create     = var.create_ssh_rsa_key_pair
  key_name   = "${local.resource_name_prefix}-key"
  public_key = trimspace(tls_private_key.ssh_tls_key.public_key_openssh)
}
