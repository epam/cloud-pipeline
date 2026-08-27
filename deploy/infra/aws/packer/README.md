# Build using packer

## Source AMI

If a source AMI is not provided explicitly - the latest Amazon Linux 2023 AMI with the 6.1 kernel
(`al2023-ami-2023.*-kernel-6.1-x86_64`, owned by `amazon`) is resolved automatically in the target region.

To pin a specific base image instead - pass `--source-ami` to the wrapper or set `source_ami`
in `$TYPE/ami.pkrvars.hcl`. Note that the AMI lookup requires `ec2:DescribeImages` permissions.

## Build using wrapper

```
# Make sure "--instance-profile" grants "AmazonSSMManagedInstanceCore" policy access or similar
build.sh --region us-east-1 \
         --instance-profile SSM_Role \
         --subnet-id subnet-xxxxxxx \
         --type cpu|gpu|all \
         [--source-ami ami-xxxxxxx]
```

## Install packer
```
cd ~
wget -q https://releases.hashicorp.com/packer/1.14.3/packer_1.14.3_linux_amd64.zip
unzip packer_1.14.3_linux_amd64.zip
```

## Build AMI
```
TYPE="cpu|gpu"
cd cloud-pipeline/deploy/infra/aws/packer

# Modify "$TYPE/ami.pkrvars.hcl" file with the actual parameters
# Make sure "iam_instance_profile" grants "AmazonSSMManagedInstanceCore" policy access or similar
~/packer init .
~/packer build --var-file="$TYPE/ami.pkrvars.hcl" .
```
