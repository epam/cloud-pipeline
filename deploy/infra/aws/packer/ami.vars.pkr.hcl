variable "instance_type" {
  type =  string
  default = ""
}
variable "region" {
  type =  string
  default = ""
}
variable "source_ami" {
  type =  string
  default = ""
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