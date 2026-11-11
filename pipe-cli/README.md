# Cloud Pipeline CLI

## Pipeline CLI features

* Pipeline CLI provides the following features:
* List pipelines
* List pipeline versions
* Run/Stop a pipeline
* View pipeline runs status
* View cluster status
* Manage data storages
* Upload/Download data
* Manage users and permissions

## Build using pip

Requirements
* Python 3.10 or later
* `pip` package manager

To install a Cloud Pipeline CLI run the following command

```bash
$ python setup.py sdist
...

# Replace X with current version and install
$ pip install dist/PipelineCLI-0.X.tar.gz
```

## Build the binaries

The binaries bundle Python 3.12 with PyInstaller. Their builds install the exact package versions of
`constraints.txt` (`pip install -r requirements.txt -c constraints.txt`), while `requirements.txt` and
`mount/requirements.txt` keep the lower bounds for the installation from the source tarball.

| Binaries | Gradle task | Script | Builder |
|---|---|---|---|
| Linux binary and tarball, with `pipe-fuse` and `pipe-omics` | `pipe-cli:buildLinux` | `build_linux.sh` | `lifescience/cloud-pipeline:pipe-cli-centos7-py312` (`docker/pipe-cli-builder-centos7`) |
| Windows | `pipe-cli:buildWin` | `build_windows.sh` | `lifescience/cloud-pipeline:pyinstaller-win64-py312` (`docker/win64-py312`) |
| macOS arm64 | `pipe-cli:buildMacArm` | `build_mac_arm.sh` | the Python 3.12 of the macOS host |

The Linux binaries are built on CentOS 7, so they run on `glibc` 2.17 or later. `build_linux.sh` checks them in a
`centos:7` container with `check_linux_bundle.sh`. It also rebuilds the bootloader of PyInstaller with
`pyinstaller/runtime-tmpdir.patch`, so the one-file binary unpacks itself into `~/.pipe/tmp`.

## Run the tests

```bash
$ pip install -r requirements.txt -r mount/requirements.txt -c constraints.txt
$ python -m pytest tests
```

## Command structure

Pipeline CLI is called using `pipe` command.

Each `pipe` command has the following structure:

```bash
pipe [COMMAND] [COMMAND_ARGUMENTS] [COMMAND_OPTIONS]
```

Example
```bash
$ pipe view-runs -s ANY -pid 1282
+-------+--------------+------------------+-----------------+---------+---------------------+
| RunID | Parent RunID |         Pipeline |         Version |  Status |             Started |
+-------+--------------+------------------+-----------------+---------+---------------------+
|  1335 |         1282 | WES Pipeline     | stable          | SUCCESS | 2018-06-19 14:41:09 |
|  1334 |         1282 | WES Pipeline     | stable          | SUCCESS | 2018-06-19 14:40:58 |
+-------+--------------+------------------+-----------------+---------+---------------------+
```
