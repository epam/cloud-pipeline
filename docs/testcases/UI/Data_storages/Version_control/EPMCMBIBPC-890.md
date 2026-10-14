# [MANUAL] Validation of mark for deletion non-empty folder for non-admin user

Test verifies that a non-admin user with full permissions on the bucket can mark a non-empty folder for deletion.

**Prerequisites**:
- A non-admin user with full permissions on the bucket

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Copy a file to the bucket at a nested path: `pipe storage cp ./{file_name} cp://{bucket_name}/{folder1_name}/{folder2_name}/{file_name}` |  |
| 2 | Copy a file to the bucket at a shallower path: `pipe storage cp ./{file_name} cp://{bucket_name}/{folder1_name}/{file_name}` |  |
| 3 | Mark the `folder1` folder for deletion: `pipe storage rm cp://{bucket_name}/{folder1_name} -r -y` | An empty list is displayed |
| 4 | List: `pipe storage ls cp://{bucket_name}` | An empty list is displayed |
| 5 | List with versions: `pipe storage ls cp://{bucket_name} -v` |  |
