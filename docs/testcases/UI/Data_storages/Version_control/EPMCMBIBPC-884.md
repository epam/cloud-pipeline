# [MANUAL] Validation hard deletion file

Test verifies that hard-deleting a file permanently removes it, including from the versioned listing.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 2 | Delete the file from the bucket: `pipe storage rm cp://{bucket_name}/{file_name} -d -y` |  |
| 3 | List with versions and details: `pipe storage ls cp://{bucket_name} -v -l` | The uploaded file is not displayed |
