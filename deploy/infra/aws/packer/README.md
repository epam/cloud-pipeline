# Build using packer

## Source AMI

If a source AMI is not provided explicitly - the latest Amazon Linux 2023 AMI with the 6.1 kernel
(`al2023-ami-2023.*-kernel-6.1-x86_64`, owned by `amazon`) is resolved automatically in the target region.

To pin a specific base image instead - pass `--source-ami` to the wrapper or set `source_ami`
in `$TYPE/ami.pkrvars.hcl`. Note that the AMI lookup requires `ec2:DescribeImages` permissions.

## Resulting AMI name and tags

The AMI is named `CloudPipeline-$TYPE-$TIMESTAMP` and is tagged with `OS_Version`, `Base_AMI_Name`,
`Type`, `SHA` and, for the `gpu` type, `Nvidia_Driver_Version`. Packer applies those tags with a `CreateTags`
call, once the AMI is available, to the AMI and to its snapshots.

## Image contents and the user data

`$TYPE/install-deps.sh` prepares Amazon Linux 2023 for the nodes: `nc`, `tc`, `iptables-legacy`, `lvm2` and `jq`
from the packages of Amazon Linux 2023, a static `btrfs` (with `mkfs.btrfs`), the cgroup v1 hierarchy, a permissive
SELinux, and the platform's Python 3.12 in `/usr/local/python3.12`, which the commit and the pause of a run use on the
node. There is no Python 2. The `gpu` type also gets the Nvidia driver, Fabric Manager and `nvidia-persistenced`.

Docker and the kubelet are not in the image: the `init_multicloud_v1.15.4.ext.sh` user data installs them, when a
node starts. Set it as the `init_script` of the image in the `amis` list of the region in `cluster.networks.config`,
e.g. `"init_script": "/opt/api/scripts/init_multicloud_v1.15.4.ext.sh"`. `init_multicloud.sh` and
`init_multicloud_v1.15.4.sh` expect them in the image.

This needs the fix of `init_multicloud_v1.15.4.ext.sh`, which downloads `init_multicloud_install_docker.sh` from
`tools/scripts/` of the distribution URL. Without it, the user data downloads the script from `//scripts/`, which
fails (HTTP 403), so the node gets no Docker and no kubelet, and never joins the cluster.

The image does not pre-load the Kubernetes system images (Calico, Flannel, `kube-proxy`, `pause`) into
`/opt/docker-system-images`, as the Amazon Linux 2 images of `deploy/infra/aws/install-*-node.sh` do. The user data
downloads them when a node starts, and falls back to that folder only if the download fails: so on this image, a node
then has no local copy of them.

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

The identity, which runs packer, requires the following permissions. They are the calls, which the packer
`amazon` plugin makes with the settings of `ami.pkr.hcl`, as checked against the plugin v1.8.4 (the version, which
`packer init` installs for `>= 1.2.8` at the time of writing):

* Always:
  * `ec2:DescribeRegions` - the region is validated before the build
  * `ec2:DescribeImages` - the source AMI lookup, the check of the new AMI name and the wait for the new AMI
  * `ec2:DescribeSubnets` - the VPC and the availability zone of `subnet_id`
  * `ec2:DescribeSecurityGroups` - the check of `security_group_ids`, or the wait for the temporary security group
  * `ec2:RunInstances`, `ec2:DescribeInstances`, `ec2:StopInstances` and `ec2:TerminateInstances` - the temporary instance
  * `ec2:DescribeVolumes` - the check for the volumes, which the temporary instance leaves behind (without it,
    packer logs an error)
  * `ec2:CreateImage` and `ec2:CreateTags` - the AMI and its tags. No `snapshot_tags` are set, but packer applies
    the AMI tags to the snapshots of the AMI as well, so `CreateTags` covers the snapshots too
* A temporary key pair, i.e. no `ssh_keypair_name`: `ec2:CreateKeyPair` and `ec2:DeleteKeyPair`
* A temporary security group, i.e. no `security_group_ids`: `ec2:CreateSecurityGroup` and
  `ec2:DeleteSecurityGroup`, plus `ec2:AuthorizeSecurityGroupIngress` for the `private_ip` interface only (the ssh
  rule; with `session_manager`, packer adds no rule)
* The `session_manager` interface only, as `private_ip` does not use an instance profile:
  * `iam:PassRole` for the role behind `iam_instance_profile` - otherwise `RunInstances` is rejected
  * `ssm:StartSession` for the instance and the `AWS-StartPortForwardingSession` document, and `ssm:TerminateSession`
  * `ec2:DescribeInstanceStatus` allows packer to close the SSM tunnel gracefully, instead of leaving
    it idle for ~20 minutes
* An account, which encrypts the EBS volumes by default with a customer managed KMS key: `kms:CreateGrant`,
  `kms:Decrypt`, `kms:DescribeKey`, `kms:GenerateDataKeyWithoutPlaintext` and `kms:ReEncrypt*` for that key, as the
  volumes of the temporary instance are encrypted with it
* No IAM read permissions: `skip_profile_validation = true` is set, so packer does not call
  `iam:GetInstanceProfile` to check the profile upfront. Without it the build fails immediately with
  `Couldn't find specified instance profile: ... AccessDenied ... iam:GetInstanceProfile`,
  even though the profile exists and is usable
* No `ec2:ModifyImageAttribute`: the AMI gets no description (see the tags above), and is not shared
* No `ssm:GetConnectionStatus`: packer calls it only with `session_manager_reconnect_on_reboot`, which is not set

## Nvidia driver (gpu)

| Variable | Environment variable | Default |
|---|---|---|
| `nvidia_driver_version` | `NVIDIA_DRIVER_VERSION` | `595.58.03` |
| `nvidia_driver_url_prefix` | `NVIDIA_DRIVER_URL_PREFIX` | *empty*, i.e. the public Nvidia locations |

Both may be set via the wrapper (`--nvidia-driver-version`, `--nvidia-driver-url-prefix`),
via `$TYPE/ami.pkrvars.hcl` or via the corresponding environment variable, if `gpu/install-deps.sh`
is executed directly. The driver version is recorded in the `Nvidia_Driver_Version` tag of the
resulting AMI.

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

Run `build.sh` with `bash` from this directory: the file is not executable, and it uses the `cpu/` and `gpu/` paths
relative to it.

```
cd cloud-pipeline/deploy/infra/aws/packer

# Make sure "--instance-profile" grants "AmazonSSMManagedInstanceCore" policy access or similar.
# It is not required, if "--ssh-interface private_ip" is used
bash build.sh --region us-east-1 \
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
