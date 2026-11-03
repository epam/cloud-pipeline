# [MANUAL] Validation of mark for deletion file for non-admin user

Test verifies that a non-admin user with full permissions on the bucket can mark a file for deletion, and that listing its versions requires elevated access.

**Prerequisites**:
- A non-admin user with full permissions on the bucket

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 2 | Mark the file for deletion: `pipe storage rm cp://{bucket_name}/{file_name} -y` |  |
| 3 | List: `pipe storage ls cp://{bucket_name}` | The file marked for deletion is not displayed |
| 4 | List with versions: `pipe storage ls cp://{bucket_name} -v` | The message `Access denied` is displayed |
