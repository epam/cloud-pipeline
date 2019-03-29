# CP CLI integration tests

CP CLI integration tests can be launched for several cloud providers. 
It is specified in `CP_PROVIDER` environment variable:

- `S3` for Aws cloud provider,
- `AZ` for Azure cloud provider,
- `GS` for Google cloud provider.

## Providers

### Aws

Aws default credentials is required to launch integration tests with Aws cloud provider.

### Azure

Several environment variables is required to launch integration tests with Azure cloud provider.

| variable | description |
| -------- | ----------- |
| **AZURE_STORAGE_ACCOUNT** | Azure storage account name. |
| **AZURE_ACCOUNT_KEY** | Azure storage account access key. |

### Google

Several environment variables is required to launch integration tests with Google cloud provider.

| variable | description |
| -------- | ----------- |
| **GOOGLE_APPLICATION_CREDENTIALS** | Local path to Google service account credentials json file. |
