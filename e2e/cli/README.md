# CP CLI integration tests

## Storage tests

CP CLI integration storage tests can be launched with all supported cloud providers. Cloud provider
is specified in `CP_PROVIDER` environment variable:

- `S3` for Aws cloud provider,
- `AZ` for Azure cloud provider,
- `GS` for Google cloud provider.

```bash
cd buckets
pytest -s -v
```

### Credentials

Storage integration tests require provider-specific credentials to be configured.

#### Aws

Aws cloud provider default credentials should be configured to launch integration tests.

#### Azure

| variable | description |
| -------- | ----------- |
| **AZURE_STORAGE_ACCOUNT** | Azure storage account name. |
| **AZURE_ACCOUNT_KEY** | Azure storage account access key. |

#### Google

| variable | description |
| -------- | ----------- |
| **GOOGLE_APPLICATION_CREDENTIALS** | Local path to Google service account credentials json file. |

### Run with docker

To run test in docker use `run_tests.sh` script and the commands below. Before launching the following common 
environment variable should be specified:

```
$API - path to Cloud Pipeline API
$API_TOKEN - admin user token
$TEST_USER - non admin user name
$USER_TOKEN - TEST_USER token
$TEST_PREFIX - this string will be added for all created test datastorages
$CP_PROVIDER
$PIPE_CLI_DOWNLOAD_URL - path to download Cloud Pipeline CLI. If this envvar is not specified the CLI will be built from repo
$GIT_BRANCH - git branch with source code
$WORKSPACE - path to Cloud Pipeline project source code
$CP_TEST_REGION_ID - the created Cloud Pipeline region ID

$RUN_TESTS_CMD="pytest --html=/home/results/report.html -s -vv --tx 4*popen//python=python2.7 --dist=loadscope /home/cloud-pipeline/e2e/cli/buckets"
$RUN_METADATA_TESTS_CMD="pytest --html=/home/results/metadata-report.html -s -vv /home/cloud-pipeline/e2e/cli/tag"
```

#### Aws

``
export CP_PROVIDER=S3
``

The following additional envvars must be specified for this provider:
`$AWS_CREDS_PATH` - path to folder with aws credentials

```
docker run --rm --env API=$API \
--env PIPE_CLI_DOWNLOAD_URL=$PIPE_CLI_DOWNLOAD_URL \
--env API_TOKEN=$API_TOKEN \
--env TEST_USER=$TEST_USER \
--env USER_TOKEN=$USER_TOKEN \
--env TEST_PREFIX=$TEST_PREFIX \
--env CP_PROVIDER=$CP_PROVIDER \
--env CP_TEST_REGION_ID=$CP_TEST_REGION_ID \
--env GIT_BRANCH=$GIT_BRANCH \
--env RUN_TESTS_CMD="$RUN_TESTS_CMD" \
--env RUN_METADATA_TESTS_CMD="$RUN_METADATA_TESTS_CMD" \
-v $AWS_CREDS_PATH:/root/.aws \
-v $WORKSPACE/results:/home/results \
-v $WORKSPACE/e2e/cli/run_tests.sh:/home/run_tests.sh \
python:2.7-stretch bash -c /home/run_tests.sh
```

#### Azure

``
export CP_PROVIDER=AZ
``

The following additional envvars must be specified for this provider:
`$AZURE_ACCOUNT_KEY` and `$AZURE_STORAGE_ACCOUNT`

```
docker run --rm --env API=$API \
--env PIPE_CLI_DOWNLOAD_URL=$PIPE_CLI_DOWNLOAD_URL \
--env API_TOKEN=$API_TOKEN \
--env TEST_USER=$TEST_USER \
--env USER_TOKEN=$USER_TOKEN \
--env TEST_PREFIX=$TEST_PREFIX \
--env CP_PROVIDER=$CP_PROVIDER \
--env CP_TEST_REGION_ID=$CP_TEST_REGION_ID \
--env GIT_BRANCH=$GIT_BRANCH \
--env RUN_TESTS_CMD="$RUN_TESTS_CMD" \
--env RUN_METADATA_TESTS_CMD="$RUN_METADATA_TESTS_CMD" \
--env AZURE_STORAGE_ACCOUNT=$AZURE_STORAGE_ACCOUNT \
--env AZURE_ACCOUNT_KEY=$AZURE_ACCOUNT_KEY \
-v $WORKSPACE/results:/home/results \
-v $WORKSPACE/e2e/cli/run_tests.sh:/home/run_tests.sh \
python:2.7-stretch bash -c /home/run_tests.sh
```

#### Google

``
export CP_PROVIDER=GS
``

The following additional envvars must be specified for this provider:
`$GOOGLE_APPLICATION_CREDENTIALS`

```
docker run --rm --env API=$API \
--env PIPE_CLI_DOWNLOAD_URL=$PIPE_CLI_DOWNLOAD_URL \
--env API_TOKEN=$API_TOKEN \
--env TEST_USER=$TEST_USER \
--env USER_TOKEN=$USER_TOKEN \
--env TEST_PREFIX=$TEST_PREFIX \
--env CP_PROVIDER=$CP_PROVIDER \
--env CP_TEST_REGION_ID=$CP_TEST_REGION_ID \
--env GIT_BRANCH=$GIT_BRANCH \
--env RUN_TESTS_CMD="$RUN_TESTS_CMD" \
--env RUN_METADATA_TESTS_CMD="$RUN_METADATA_TESTS_CMD" \
--env GOOGLE_APPLICATION_CREDENTIALS=/root/gcp-creds.json \
-v $GOOGLE_APPLICATION_CREDENTIALS:/root/gcp-creds.json \
-v $WORKSPACE/results:/home/results \
-v $WORKSPACE/e2e/cli/run_tests.sh:/home/run_tests.sh \
python:2.7-stretch bash -c /home/run_tests.sh
```
