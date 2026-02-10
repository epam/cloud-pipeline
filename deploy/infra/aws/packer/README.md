# Build using packer

## Build using wrapper

```
# Make sure "--instance-profile" grants "AmazonSSMManagedInstanceCore" policy access or similar
build.sh --region us-east-1 \
         --instance-profile SSM_Role \
         --subnet-id subnet-xxxxxxx \
         --type cpu|gpu|all
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
