# [MANUAL] Validation hard deletion non-empty folder

Test verifies that hard-deleting a non-empty folder permanently removes it, including from the versioned listing.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Copy a file to the bucket at a nested path: `pipe storage cp ./{file_name} cp://{bucket_name}/{folder1_name}/{folder2_name}/{file_name}` |  |
| 2 | Copy a file to the bucket at a shallower path: `pipe storage cp ./{file_name} cp://{bucket_name}/{folder1_name}/{file_name}` |  |
| 3 | Delete the `folder1` folder from the bucket: `pipe storage rm cp://{bucket_name}/{folder1_name} -r -d -y` |  |
| 4 | List: `pipe storage ls cp://{bucket_name}` | The `folder1` folder is not displayed |
| 5 | List with versions: `pipe storage ls cp://{bucket_name} -v` | The `folder1` folder is not displayed |
