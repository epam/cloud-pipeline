# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This file has two jobs. First, pytest adds the directory holding the topmost `conftest.py` to
# `sys.path`, which is what lets `tests/` write `import sas` without the package having been installed
# first - deleting the file makes every test fail with `ModuleNotFoundError: No module named 'sas'`.
#
# Second, the fixture below. Beyond it there are none: the fakes live in `tests/fakes.py`, because they
# are plain classes rather than fixtures and reading them next to the tests is easier than chasing an
# injected name.

import pytest


@pytest.fixture(autouse=True)
def isolated_aws_environment(monkeypatch):
    """
    Cuts every test off from this machine's AWS configuration.

    `autouse=True` applies it to every test without any of them asking. It is needed because building an
    S3 client is the subject of `tests/test_aws.py` and happens inside the worker and janitor tests too,
    and `boto3.Session().get_credentials()` walks the real credential chain: it reads `~/.aws`, honours
    `AWS_PROFILE`, and on a host with nothing configured it probes the instance metadata endpoint over
    the network. That would make the suite depend on the developer's own cloud setup - a test failing
    with `ProfileNotFound` because of an environment variable set for something else entirely - and
    would break the promise in the README that the tests touch nothing outside the process.

    The two file variables point at a path that does not exist, which botocore treats as an empty
    configuration rather than an error. `AWS_EC2_METADATA_DISABLED` is what keeps it off the network.
    """
    for leftover in ('AWS_PROFILE', 'AWS_DEFAULT_PROFILE', 'AWS_ROLE_ARN',
                     'AWS_WEB_IDENTITY_TOKEN_FILE', 'AWS_CONTAINER_CREDENTIALS_RELATIVE_URI'):
        monkeypatch.delenv(leftover, raising=False)
    monkeypatch.setenv('AWS_CONFIG_FILE', '/nonexistent/aws/config')
    monkeypatch.setenv('AWS_SHARED_CREDENTIALS_FILE', '/nonexistent/aws/credentials')
    monkeypatch.setenv('AWS_EC2_METADATA_DISABLED', 'true')
    monkeypatch.setenv('AWS_ACCESS_KEY_ID', 'test-access-key-id')
    monkeypatch.setenv('AWS_SECRET_ACCESS_KEY', 'test-secret-access-key')
    monkeypatch.setenv('AWS_DEFAULT_REGION', 'eu-central-1')
