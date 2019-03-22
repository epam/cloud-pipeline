# Integration tests for CLI

## Requirements

* Python 2.7 or Python 3.6
* `pip` package manager
* Cloud Pipeline CLI
* AWS credentials
* Environment variables API (api host url) and API_TOKEN (server api token) or ~/.pipe/config.json configuration file

## Installation

* To install a Cloud Pipeline CLI run the following command:

```bash
$ python setup.py sdist
...

$ cd dist

# Replace X with current version
$ pip install PipelineCLI-0.X.tar.gz
```

* AWS python client library:

```
$ pip install boto3
```

* Python library for testing:

```
$ pip install pytest
```

* Python library for generating html report:

```
$ pip install pytest-html
```

* Python library for running test simultaneously:

```
$ pip install pytest-xdist
```

## Launching

To run all tests in folder use the following command:

```
$ pytest path/to/test/folder/
```

To run single test use the following command:

```
$ pytest <test_file_name>.py
```

To generate html report add the following command:

```
$ pytest --html=path/to/report.html
```

To increase verbosity use:

```
$ pytest -v
```

To run tests in parallel mode use:

```
$ pytest -n <number of CPUs> --dist=loadscope
```

Log can be found in tests.log file.

Note: launching tests should be done from the directory where the tests are located.

## Examples

Single test run:

```
$ pytest --html=report.html -v test_kill_node_manually.py
```

Run all tests:

```
$ pytest --html=report.html -v -n 4
```

## Future development

Each new test class or test must begin with `test_`.