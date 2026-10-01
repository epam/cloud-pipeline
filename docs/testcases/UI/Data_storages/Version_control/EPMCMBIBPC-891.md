# [MANUAL] [Negative] Validation of file versions for non-admin and non-owner user

Test verifies that a non-admin, non-owner user can list a file's latest version but is denied when listing all its versions.

**Prerequisites**:
- A non-admin user with full permissions on the bucket (the bucket must be created by an admin)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the non-admin user |  |
| 2 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 3 | Copy a file with the same name and a different size to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 4 | List with details: `pipe storage ls cp://{bucket_name} -l` | One record about the file is displayed, the **Size** field shows the size of the last uploaded file |
| 5 | List with versions and details: `pipe storage ls cp://{bucket_name} -v -l` | The message `Access denied` is displayed |
