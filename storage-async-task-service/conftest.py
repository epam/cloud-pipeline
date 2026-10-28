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

# pytest adds the directory of the topmost `conftest.py` to `sys.path`, which is what lets
# `tests/` import `sats` without installing it. The only fixture is below; the fakes are plain
# classes in `tests/fakes.py`.

import pytest


@pytest.fixture(autouse=True)
def isolated_aws_environment(monkeypatch):
    """Cuts every test off from this machine's AWS configuration.

    `boto3.Session().get_credentials()` walks the real chain - `~/.aws`, `AWS_PROFILE`, then the
    instance metadata endpoint over the network - so the suite would otherwise depend on the
    developer's own cloud setup. The file variables point at a path that does not exist, which
    botocore treats as an empty configuration, and `AWS_EC2_METADATA_DISABLED` is what keeps it off
    the network.
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
