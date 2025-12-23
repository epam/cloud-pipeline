packer {
  required_plugins {
    amazon = {
      version = ">= 1.2.8"
      source  = "github.com/hashicorp/amazon"
    }
  }
}

locals {
  timestamp = formatdate("YYYYMMDDHHmmss", timestamp())
}

source "amazon-ebs" "cloud-pipeline-cpu" {
  ami_name             = "CloudPipeline-CPU-${local.timestamp}"
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
  }
}

build {
  name = "cloud-pipeline-cpu"
  sources = [
    "source.amazon-ebs.cloud-pipeline-cpu"
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