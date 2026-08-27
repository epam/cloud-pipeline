# Build using packer

## Source AMI

If a source AMI is not provided explicitly - the latest Amazon Linux 2023 AMI with the 6.1 kernel
(`al2023-ami-2023.*-kernel-6.1-x86_64`, owned by `amazon`) is resolved automatically in the target region.

To pin a specific base image instead - pass `--source-ami` to the wrapper or set `source_ami`
in `$TYPE/ami.pkrvars.hcl`. Note that the AMI lookup requires `ec2:DescribeImages` permissions.

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
