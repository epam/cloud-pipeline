# [MANUAL] Validation of mark for deletion file

Test verifies that marking a file for deletion hides it from the plain listing but shows it in the versioned listing.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Run `pipe storage create -v ...` |  |
| 2 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 3 | Mark the file for deletion: `pipe storage rm cp://{bucket_name}/{file_name} -y` |  |
| 4 | List: `pipe storage ls cp://{bucket_name}` | An empty list is displayed |
| 5 | List with versions: `pipe storage ls cp://{bucket_name} -v` | The name of the file deleted at step 3 is displayed |
