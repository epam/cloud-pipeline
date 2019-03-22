# Cloud Pipeline CLI

## Pipeline CLI features

* Pipeline CLI provides the following features:
* List pipelines
* List pipeline versions
* Run/Stop a pipeline
* View pipeline runs status
* View cluster status

## Installation

Requirements
* Python 2.7 or Python 3.6
* `pip` package manager

To install a Cloud Pipeline CLI run the following command

```bash
$ python setup.py sdist
...

$ cd dist

# Replace X with current version
$ pip install Pipeline CLI-0.X.tar.gz
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
|  1335 |         1282 | Capture Pipeline | stable.8threads | SUCCESS | 2017-06-19 14:41:09 |
|  1334 |         1282 | Capture Pipeline | stable.8threads | SUCCESS | 2017-06-19 14:40:58 |
+-------+--------------+------------------+-----------------+---------+---------------------+
```

## Testing samples commands

### Process 1_Exome with Capture pipe
```bash
pipe run --pipeline "Capture Pipeline@draft-975da79b" \
        --read2 s3://cmbi-analysis/Validation/ExomeBatch/PipelineInputData/FASTQ/1_Exome_R2.fastq.gz \
        --s3-result s3://cmbi-analysis/Validation/ExomeBatch/1339 \
        --sample 1_Exome \
        --read1 s3://cmbi-analysis/Validation/ExomeBatch/PipelineInputData/FASTQ/1_Exome_R1.fastq.gz \
        --sample-sheet s3://cmbi-analysis/Validation/ExomeBatch/PipelineInputData/SampleSheet.Full.20170622.csv
```

# Build a single-file distribution
```
pyinstaller     --hidden-import=UserList \
                --hidden-import=UserString \
                --hidden-import=commands \
                --add-data "res/effective_tld_names.dat.txt:tld/res/" \
                pipe.py
```

# Configuration of the Install/Configure options

Install:
```
{"Linux": "mkdir -p ~/.pipe\nwget -k '${base.pipe.distributions.url}pipe' -O ~/.pipe/pipe\nchmod +x ~/.pipe/pipe\nexport PATH=$PATH:~/.pipe/", "Windows": "1. Download CLI using URL: ${base.pipe.distributions.url}pipe.zip\n2. Extract archieve to C:\\Users\\<YOUR_NAME>\\.pipe\n- Navigate to C:\\Users\\<YOUR_NAME>\\Downloads\n- Right-click 'pipe.zip' and click 'Extract all'\n- Enter 'C:\\Users\\<YOUR_NAME>\\.pipe\\' in the 'Destination path'\n- Click 'Extract'\n3. Open Windows Console and add CLI to the PATH\n- Click 'Start' and run 'cmd' application\n- In the Console window type 'setx PATH \"%PATH%;%userprofile%\\.pipe\\pipe\\\"'", "Other": "mkdir -p ~/.pipe\nwget -k \"${base.pipe.distributions.url}PipelineCLI.tar.gz\" -O ~/.pipe/PipelineCLI.tar.gz\npip install --user ~/.pipe/PipelineCLI.tar.gz"}
```

Configure:
```
{"Linux": "pipe configure --auth-token {user.jwt.token} --api ${base.api.host} --timezone local --proxy ''", "Windows": "pipe configure --auth-token {user.jwt.token} --api ${base.api.host} --timezone local --proxy 'pac'", "Other": "pipe configure --auth-token {user.jwt.token} --api ${base.api.host} --timezone local --proxy ''"}
```