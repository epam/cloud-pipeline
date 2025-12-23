## Install packer
```
cd ~
wget -q https://releases.hashicorp.com/packer/1.14.3/packer_1.14.3_linux_amd64.zip
unzip packer_1.14.3_linux_amd64.zip
```

## Build AMI
```
TYPE="cpu|gpu"
OS="amzn2023"
KERNEL="6.1"

~/packer init .

# Modify "ami.pkrvars.hcl" file with the actual parameters
# Make sure "iam_instance_profile" grants "AmazonSSMManagedInstanceCore" policy access or similar
~/packer build --var-file="$TYPE-$OS-$KERNEL/ami.pkrvars.hcl" .
```