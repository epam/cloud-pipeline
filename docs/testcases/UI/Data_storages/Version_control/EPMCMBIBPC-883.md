# [MANUAL] Validation of restore specified version

Test verifies restoring a specific earlier version of a file (not the latest) by its Version value.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 2 | Copy a file with the same name and a different size to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 3 | Delete the file from the bucket: `pipe storage rm cp://{bucket_name}/{file_name} -y` |  |
| 4 | List with versions and details: `pipe storage ls cp://{bucket_name} -v -l` |  |
| 5 | Copy the value from the **Version** field for the file uploaded at step 1 (not marked `latest`) |  |
| 6 | Restore the file uploaded at step 1: `pipe storage restore cp://{bucket_name}/{file_name} -v {file_version}` |  |
| 7 | List with details: `pipe storage ls cp://{bucket_name} -l` | The file is displayed with the **Size** field equal to the size of the file uploaded at step 1 |
