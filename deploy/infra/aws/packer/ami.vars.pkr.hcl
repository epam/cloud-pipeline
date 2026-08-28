variable "instance_type" {
  type =  string
  default = ""
}
variable "region" {
  type =  string
  default = ""
}
variable "source_ami" {
  # If empty - the latest Amazon Linux 2023 AMI with the 6.1 kernel is used
  type =  string
  default = ""
}
variable "source_ami_architecture" {
  type =  string
  default = "x86_64"
}
variable "ssh_username" {
  type =  string
  default = ""
}
variable "iam_instance_profile" {
  type =  string
  default = ""
}
variable "subnet_id" {
  type =  string
  default = ""
}
variable "security_group_ids" {
  # IDs (not names) of the existing security groups to assign to the temporary instance.
  # If empty - a temporary security group is created by packer
  type =  list(string)
  default = []
}
variable "ssh_keypair_name" {
  # Name of the existing EC2 key pair to access the temporary instance.
  # Requires "ssh_private_key_file" to be set as well.
  # If empty - a temporary key pair is created by packer
  type =  string
  default = ""
}
variable "ssh_private_key_file" {
  # Path to the private key, which matches "ssh_keypair_name"
  type =  string
  default = ""
}
variable "deps_file" {
  type =  string
  default = ""
}
variable "ami_type" {
  type =  string
  default = ""
}
variable "nvidia_driver_version" {
  # Version of the Nvidia driver and the matching Nvidia rpm packages
  type =  string
  default = "595.58.03"
}
variable "nvidia_driver_url_prefix" {
  # Location, e.g. an internal mirror, which serves a single "$nvidia_driver_version.tgz" archive
  # with the driver runfile and the Nvidia rpm packages inside.
  # If empty - the public Nvidia locations are used
  type =  string
  default = ""
}