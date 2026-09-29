# [MANUAL] [Negative] Validation hard deletion file for non-admin and non-owner user

Test verifies that a non-admin, non-owner user with full permissions on the bucket cannot hard-delete a file.

**Prerequisites**:
- A non-admin user with full permissions on the bucket (the bucket must be created by an admin)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the non-admin user |  |
| 2 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 3 | Delete the file from the bucket: `pipe storage rm cp://{bucket_name}/{file_name} -d -y` | The message `Access denied` is displayed |
