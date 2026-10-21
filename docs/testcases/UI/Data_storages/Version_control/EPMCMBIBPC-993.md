# [MANUAL] Validation of hard deletion of marked for delete file

Test verifies that hard-deleting a file marked for deletion permanently removes it.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Run `pipe storage create -v ...` |  |
| 2 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 3 | Mark the file for deletion: `pipe storage rm cp://{bucket_name}/{file_name} -y` |  |
| 4 | Delete the file marked for deletion: `pipe storage rm cp://{bucket_name}/{file_name} -y -d` |  |
| 5 | List: `pipe storage ls cp://{bucket_name} -v -l` | An empty list is displayed |
