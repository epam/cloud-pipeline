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
