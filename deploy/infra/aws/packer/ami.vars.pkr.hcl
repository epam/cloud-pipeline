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