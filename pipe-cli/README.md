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
* Python 2.7 or Python 3.6
* `pip` package manager

To install a Cloud Pipeline CLI run the following command

```bash
$ python setup.py sdist
...

$ cd dist

# Replace X with current version and install
$ pip install Pipeline CLI-0.X.tar.gz
```

## Build a single-file distribution
```
pyinstaller     --hidden-import=UserList \
                --hidden-import=UserString \
                --hidden-import=commands \
                --add-data "res/effective_tld_names.dat.txt:tld/res/" \
                pipe.py
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
