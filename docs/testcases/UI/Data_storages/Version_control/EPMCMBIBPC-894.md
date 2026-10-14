# [MANUAL] [Negative] Validation of restore marked for deletion file for non admin and non-owner user

Test verifies that a non-admin, non-owner user with full permissions on the bucket cannot restore a file marked for deletion.

**Prerequisites**:
- A non-admin user with full permissions on the bucket (the bucket must be created by an admin)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the non-admin user |  |
| 2 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 3 | Mark the file for deletion: `pipe storage rm cp://{bucket_name}/{file_name} -y` |  |
| 4 | List: `pipe storage ls cp://{bucket_name}` |  |
| 5 | Restore the file: `pipe storage restore cp://{bucket_name}/{removed_file_name}` | The message `Access denied` is displayed |
