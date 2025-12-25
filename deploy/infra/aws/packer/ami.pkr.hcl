packer {
  required_plugins {
    amazon = {
      version = ">= 1.2.8"
      source  = "github.com/hashicorp/amazon"
    }
    git = {
      version = ">= 0.6.2"
      source  = "github.com/ethanmdavidson/git"
    }
  }
}

data "git-commit" "cwd-head" { }

locals {
  timestamp = formatdate("YYYYMMDDHHmmss", timestamp())
  truncated_sha = substr(data.git-commit.cwd-head.hash, 0, 8)
}

source "amazon-ebs" "cloud-pipeline-ami" {
  ami_name             = "CloudPipeline-${var.ami_type}-${local.timestamp}"
  instance_type        = "${var.instance_type}"
  region               = "${var.region}"
  source_ami           = "${var.source_ami}"
  ssh_username         = "${var.ssh_username}"
  ssh_interface        = "session_manager"
  communicator         = "ssh"
  iam_instance_profile = "${var.iam_instance_profile}"
  subnet_id            = "${var.subnet_id}"
  tags = {
      OS_Version = "amzn2023"
      Base_AMI_Name = "{{ .SourceAMIName }}"
      Type = "${var.ami_type}"
      SHA = "${local.truncated_sha}"
  }
}

build {
  name = "cloud-pipeline-ami"
  sources = [
    "source.amazon-ebs.cloud-pipeline-ami"
  ]

  provisioner "file" {
    source = "${var.deps_file}"
    destination = "/tmp/install-deps.sh"
  }

  provisioner "shell" {
    inline = [
      "sudo bash /tmp/install-deps.sh"
    ]
  }
}