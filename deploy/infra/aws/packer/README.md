# Build using packer

## Source AMI

If a source AMI is not provided explicitly - the latest Amazon Linux 2023 AMI with the 6.1 kernel
(`al2023-ami-2023.*-kernel-6.1-x86_64`, owned by `amazon`) is resolved automatically in the target region.

To pin a specific base image instead - pass `--source-ami` to the wrapper or set `source_ami`
in `$TYPE/ami.pkrvars.hcl`. Note that the AMI lookup requires `ec2:DescribeImages` permissions.

## Security group and SSH key pair

By default packer creates a temporary security group and a temporary key pair for the temporary
instance and removes them afterwards. Existing ones may be used instead:

| Variable | Wrapper option | Default |
|---|---|---|
| `security_group_ids` | `--security-group-ids sg-xxx[,sg-yyy]` | *empty*, i.e. a temporary security group is created |
| `ssh_keypair_name` | `--ssh-keypair-name` | *empty*, i.e. a temporary key pair is created |
| `ssh_private_key_file` | `--ssh-private-key-file` | *empty* |

`ssh_keypair_name` and `ssh_private_key_file` shall be set together - the private key, which matches
the key pair, is required to connect to the instance.

The build connects to the instance via SSM Session Manager (`ssh_interface = "session_manager"`),
so a custom security group needs **no inbound rules at all**. It shall allow outbound traffic to:

* `443` for the SSM endpoints (`ssm`, `ssmmessages`, `ec2messages`) - otherwise the instance does not
  register in SSM and the build fails waiting for the ssh connection
* `443`/`80` for the packages, which are installed by `$TYPE/install-deps.sh`: Amazon Linux 2023 repositories,
  `cloud-pipeline-oss-builds.s3.*` and, for the `gpu` type, the Nvidia locations
  (see [Nvidia driver](#nvidia-driver-gpu) - an internal mirror may be used instead)

The subnet shall also have a route to those destinations, i.e. a NAT/Internet gateway or the corresponding
VPC endpoints. Additionally `ec2:DescribeInstanceStatus` permissions allow packer to close the SSM
tunnel gracefully, instead of leaving it idle for ~20 minutes.

## Nvidia driver (gpu)

| Variable | Environment variable | Default |
|---|---|---|
| `nvidia_driver_version` | `NVIDIA_DRIVER_VERSION` | `595.58.03` |
| `nvidia_driver_url_prefix` | `NVIDIA_DRIVER_URL_PREFIX` | *empty*, i.e. the public Nvidia locations |

Both may be set via the wrapper (`--nvidia-driver-version`, `--nvidia-driver-url-prefix`),
via `$TYPE/ami.pkrvars.hcl` or via the corresponding environment variable, if `gpu/install-deps.sh`
is executed directly. The driver version is recorded in the resulting AMI description, e.g.
`Cloud Pipeline gpu node image (Amazon Linux 2023, Nvidia driver 595.58.03)`.

### Default (public Nvidia locations)

If `nvidia_driver_url_prefix` is not set, the driver runfile and the rpm packages are downloaded from Nvidia:

* `https://us.download.nvidia.com/tesla/$version/NVIDIA-Linux-$(arch)-$version.run`
* `https://developer.download.nvidia.com/compute/cuda/repos/amzn2023/x86_64/<package>-$version-1.amzn2023.x86_64.rpm`
  for `nvidia-fabricmanager`, `libnvidia-cfg` and `nvidia-persistenced`

### Custom location (e.g. an internal mirror)

If `nvidia_driver_url_prefix` is set, e.g. to `https://server/tools/nvidia/drivers/`, everything is
taken from a single archive at `$nvidia_driver_url_prefix/$version.tgz`, e.g.
`https://server/tools/nvidia/drivers/595.58.03.tgz`. The archive is unpacked and:

* exactly one `*.run` file is expected at any depth - it is installed as the driver (the build fails otherwise)
* all `*.rpm` files found are installed in a single `yum install` call, so that dependencies between
  them are resolved (at least one is expected, as `nvidia-fabricmanager` and `nvidia-persistenced`
  are enabled afterwards)

## Build using wrapper

```
# Make sure "--instance-profile" grants "AmazonSSMManagedInstanceCore" policy access or similar
build.sh --region us-east-1 \
         --instance-profile SSM_Role \
         --subnet-id subnet-xxxxxxx \
         --type cpu|gpu|all \
         [--source-ami ami-xxxxxxx] \
         [--security-group-ids sg-xxxxxxx,sg-yyyyyyy] \
         [--ssh-keypair-name my-key --ssh-private-key-file ~/.ssh/my-key.pem] \
         [--nvidia-driver-version 595.58.03] \
         [--nvidia-driver-url-prefix https://server/tools/nvidia/drivers/]
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
