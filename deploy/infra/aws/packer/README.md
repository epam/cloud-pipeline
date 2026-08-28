# Build using packer

## Source AMI

If a source AMI is not provided explicitly - the latest Amazon Linux 2023 AMI with the 6.1 kernel
(`al2023-ami-2023.*-kernel-6.1-x86_64`, owned by `amazon`) is resolved automatically in the target region.

To pin a specific base image instead - pass `--source-ami` to the wrapper or set `source_ami`
in `$TYPE/ami.pkrvars.hcl`. Note that the AMI lookup requires `ec2:DescribeImages` permissions.

## Connection to the temporary instance

Packer provisions the image over ssh. The way the ssh connection is established is controlled
by `ssh_interface` (`--ssh-interface`):

| Value | Connection | Requirements |
|---|---|---|
| `session_manager` (default) | A tunnel via SSM, no inbound access to the instance | `iam_instance_profile` with `AmazonSSMManagedInstanceCore`, `iam:PassRole` for the build identity, `session-manager-plugin` installed next to packer, outbound `443` to the SSM endpoints |
| `private_ip` | A direct ssh connection to the private IP of the instance | Inbound access to port `22` from the packer host, which shall be able to reach the private IP, i.e. run in the same VPC or via peering/VPN |

`private_ip` requires no IAM permissions at all - `--instance-profile` becomes optional and is not
requested from AWS. This is the option to use, if the build identity is not allowed to pass roles.
Public IPs are not used in either case.

## Security group and SSH key pair

By default packer creates a temporary security group and a temporary key pair for the temporary
instance and removes them afterwards. Existing ones may be used instead:

| Variable | Wrapper option | Default |
|---|---|---|
| `security_group_ids` | `--security-group-ids sg-xxx[,sg-yyy]` | *empty*, i.e. a temporary security group is created |
| `temporary_security_group_source_cidrs` | `--temporary-sg-source-cidrs CIDR[,CIDR]` | *empty*, i.e. packer allows `0.0.0.0/0` |
| `ssh_keypair_name` | `--ssh-keypair-name` | *empty*, i.e. a temporary key pair is created |
| `ssh_private_key_file` | `--ssh-private-key-file` | *empty* |

`ssh_keypair_name` and `ssh_private_key_file` shall be set together - the private key, which matches
the key pair, is required to connect to the instance.

Inbound rules depend on `ssh_interface`:

* `session_manager` - **no inbound rules are required at all**
* `private_ip` - inbound TCP `22` from the packer host is required. A temporary security group allows
  it from `0.0.0.0/0`, unless `temporary_security_group_source_cidrs` narrows that down. Note that
  a custom `security_group_ids` is used as is, i.e. packer does not add any rules to it, so it shall
  already allow the ssh access

`temporary_security_group_source_cidrs` is ignored, when `security_group_ids` is set.

In both cases the security group shall allow outbound traffic to:

* `443`/`80` for the packages, which are installed by `$TYPE/install-deps.sh`: Amazon Linux 2023 repositories,
  `cloud-pipeline-oss-builds.s3.*` and, for the `gpu` type, the Nvidia locations
  (see [Nvidia driver](#nvidia-driver-gpu) - an internal mirror may be used instead)
* `443` for the SSM endpoints (`ssm`, `ssmmessages`, `ec2messages`) - for the `session_manager`
  interface only, otherwise the instance does not register in SSM and the build fails waiting
  for the ssh connection

The subnet shall also have a route to those destinations, i.e. a NAT/Internet gateway or the corresponding
VPC endpoints.

## IAM permissions of the build identity

The identity, which runs packer, requires the usual EC2 build permissions (`RunInstances`,
`CreateImage`, `DescribeImages`, `CreateTags`, etc.) and:

* No IAM read permissions: `skip_profile_validation = true` is set, so packer does not call
  `iam:GetInstanceProfile` to check the profile upfront. Without it the build fails immediately with
  `Couldn't find specified instance profile: ... AccessDenied ... iam:GetInstanceProfile`,
  even though the profile exists and is usable
* `iam:PassRole` for the role behind `iam_instance_profile` - otherwise `RunInstances` is rejected.
  Required for the `session_manager` interface only, as `private_ip` does not use an instance profile
* `ec2:DescribeInstanceStatus` allows packer to close the SSM tunnel gracefully, instead of leaving
  it idle for ~20 minutes (`session_manager` only)

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
# Make sure "--instance-profile" grants "AmazonSSMManagedInstanceCore" policy access or similar.
# It is not required, if "--ssh-interface private_ip" is used
build.sh --region us-east-1 \
         --instance-profile SSM_Role \
         --subnet-id subnet-xxxxxxx \
         --type cpu|gpu|all \
         [--source-ami ami-xxxxxxx] \
         [--ssh-interface session_manager|private_ip] \
         [--security-group-ids sg-xxxxxxx,sg-yyyyyyy] \
         [--temporary-sg-source-cidrs 10.0.0.0/8] \
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
