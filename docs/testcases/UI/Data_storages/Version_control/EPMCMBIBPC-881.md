# [MANUAL] Validation of restore marked for deletion file

Test verifies that restoring a file marked for deletion brings it back to the plain listing.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Copy a file to the bucket: `pipe storage cp ./{file_name} cp://{bucket_name}/{file_name}` |  |
| 2 | Mark the file for deletion: `pipe storage rm cp://{bucket_name}/{file_name} -y` |  |
| 3 | List: `pipe storage ls cp://{bucket_name}` | An empty list is displayed |
| 4 | List with versions: `pipe storage ls cp://{bucket_name} -v` | The name of the file deleted at step 2 is displayed |
| 5 | Restore the file: `pipe storage restore cp://{bucket_name}/{removed_file_name}` |  |
| 6 | List: `pipe storage ls cp://{bucket_name}` | The restored file is displayed |
