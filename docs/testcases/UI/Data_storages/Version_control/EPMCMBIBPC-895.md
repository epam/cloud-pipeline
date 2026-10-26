# [MANUAL] [Negative] Validation hard deletion non-empty folder for non-admin and non-owner user

Test verifies that a non-admin, non-owner user with full permissions on the bucket cannot hard-delete a non-empty folder.

**Prerequisites**:
- A non-admin user with full permissions on the bucket (the bucket must be created by an admin)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the non-admin user |  |
| 2 | Copy a file to the bucket at a nested path: `pipe storage cp ./{file_name} cp://{bucket_name}/{folder1_name}/{folder2_name}/{file_name}` |  |
| 3 | Copy a file to the bucket at a shallower path: `pipe storage cp ./{file_name} cp://{bucket_name}/{folder1_name}/{file_name}` |  |
| 4 | Delete the `folder1` folder from the bucket: `pipe storage rm cp://{bucket_name}/{folder1_name} -r -d -y` | The message `Access denied` is displayed |
