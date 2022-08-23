# Cloud Pipeline Storage Lifecycle Service

Storage Lifecycle Service is a python application to manage and execute storage lifecycle rules setup for Cloud Pipeline `Data Storages`

## Requirements

- Python 3.7
- attrs 21.2.0 
- dataclasses 0.6
- schedule 1.1.0 
- boto3 1.24.46
- botocore 1.27.46
- DateTime 4.3 
- parameterized 0.8.1
- pipe-common

## Launching

Example of launching `Storage Lifecycle Service`:
```commandline
python cp-storage-lifecycle-service.py
         --cp-api-url=<value> \
        [--cp-api-token="<value>"]
        [--mode=<value>]
        [--at=<value>]
```
**--cp-api-url** - URL for Cloud-Pipeline REST API instance

**--cp-api-token** (not required) - Cloud-Pipeline JWT token, if not provided application will try to get in from **API_TOKEN** env var

**--mode=<value>** (not required, default: single) - Application mode single/daemon to run application. 

    When **single** mode is used, program will perform one sync iteration and quit. Good fit in sutiations where external sheduling program is used
    When **daemon** mode is used, program will be run as daemon and once in a day will perform sync iteration

**--at** (not required, default: 00:01) - Used with --mode=daemon, use when you need to specify a time when sync process should be performed

**--max-execution-running-days** - How long execution can be running before it will be treated as FAILED

Storage Lifecycle Service expects that Cloud-Pipeline environment has configured System Preference `storage.lifecycle.service.cloud.config` with the next values:
```
{
    "S3": {
        "tagging_job_aws_account_id": "<AWS account id where batch tagging job will be performed>",
        "tagging_job_role_arn": "<AWS IAM Role ARN that will be used to perform batch tagging job>",
        "tagging_job_report_bucket": "<name of the bucket that will be used as temporary store for internal files like s3 object tagging manifest, report files etc>",
        "tagging_job_report_bucket_prefix (Optional)": "<bucket location prefix under which internal files will be stored>",
        "tagging_job_poll_status_retry_count (Optional)": "<how much job status polls perform before fail>",
        "tagging_job_poll_status_sleep_sec (Optional)": "<Lenght of the pause to perform before next poll, in sec>" 
    }
}
```

## Troubleshooting

For troubleshooting logs are available within the application.
Additionally, there are some cloud specific aspects that also could be helpful to troubleshoot.  

### AWS

**NOTE:** Please see the link to get some useful information on several limitations regarding lifecycle policies: [AWS S3 Lifecycle Docs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-transition-general-considerations.html)

When application actually do some work on a cloud (tagging files), it creates several internal files like tagging manifest and job reports.
Manifest and reports for successfully completed jobs will be deleted right after job is complete, but for failed job such files are kept for further investigation.

Here the structure of `system` bucket with internal files:
```
sustem-bucket:
  - <system-bucket-prefix> 
     - <storage-name-1>/
         - rule_<rule_id-1>/
             - <bucket-path-1>/
                 - <storage-name-1>_<bucket-path-1>_<storage-class>_<date>.csv    # - tagging manifest file (discribes what to tag)
                 - job-<job-id>/
                     - manifest.json  # - job manifest file describes result file content
                     - results/
                         - <hash>.csv - information about each file tagging process 
             - ...
         - rule_<rule_id-2>/
    - <storage-name-2>/
         - ...
```