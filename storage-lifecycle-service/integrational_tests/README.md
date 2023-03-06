# Cloud Pipeline Storage Lifecycle Service Integration tests

This package provides integration tests for SLS. 
These tests will actually interact with Cloud-Pipeline environment and Cloud Provider (such as S3), to check logic of SLS, so some specific information regarding particular Cloud-Pipeline env or Cloud Provider is required.

## Prerequisites

 - CP_API_URL 
```
URL for Cloud-Pipeline REST API instance
```

 - API_TOKEN
```
Cloud-Pipeline JWT token

```

 - CP_STORAGE_LIFECYCLE_DAEMON_AWS_CONFIG
```
Configuration string to work with AWS S3 storages, possible values:

    - system_bucket - name of the bucket that will be used as temporary store for internal files like s3 object tagging manifest, report files etc
    - system_bucket_prefix - bucket location prefix under which internal files will be stored
    - role_arn - AWS IAM Role ARN that will be used to perform batch tagging job
    - aws_account_id - AWS account id where batch tagging job will be performed
    - tagging_job_poll_status_retry_count - how much job status polls perform before fail
    - tagging_job_poll_status_sleep_sec - lenght of the pause to perform before next poll, in sec
```

 - CP_STORAGE_LIFECYCLE_DAEMON_AWS_REGION_ID
```
AWS Region ID - ID of registered region in Cloud-Pipeline environment, this region will be used to create storages from testcases files
```

 - CP_STORAGE_LIFECYCLE_DAEMON_TEST_CASES_PATH
```
Path to test case files
```

## Launching

1) Export all variables from **Prerequisites** section
2) Install all dependencies from setup.py + pipe-common package
3) Run tests

```
python integration_test_runner.py
```