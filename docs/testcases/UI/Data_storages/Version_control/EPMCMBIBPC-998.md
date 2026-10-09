# [MANUAL] Validation of hard deletion of marked for non-empty folder

Test verifies that hard-deleting a non-empty folder marked for deletion permanently removes it.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Copy a file to the bucket at a nested path: `pipe storage cp ./{file_name} cp://{bucket_name}/{folder1_name}/{folder2_name}/{file_name}` |  |
| 2 | Copy a file to the bucket at a shallower path: `pipe storage cp ./{file_name} cp://{bucket_name}/{folder1_name}/{file_name}` |  |
| 3 | Mark the `folder1` folder for deletion: `pipe storage rm cp://{bucket_name}/{folder1_name} -r -y` |  |
| 4 | Delete the `folder1` folder from the bucket: `pipe storage rm cp://{bucket_name}/{folder1_name} -r -d -y` |  |
| 5 | List with versions shown: `pipe storage ls cp://{bucket_name} -v` | The deleted folder is not displayed |
