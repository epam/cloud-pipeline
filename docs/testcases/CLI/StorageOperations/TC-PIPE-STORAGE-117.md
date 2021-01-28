# Validation of restore specified version

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 2. Upload the file with the same name as at step 1 but with another size to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 3. Remove the copied file from the storage | `pipe storage rm cp://{storage_name}/{file_name} -y` |  |
| 4. List the storage with versions and details displaying | `pipe storage ls cp://{storage_name} -v -l` |  |
| 5. Save the value from the `Version` field for the file uploaded at step 1 (without `latest` mark) |  |  |
| 6. Perform the restore of the file uploaded at step 1 | `pipe storage restore cp://{storage_name}/{file_name} -v {file_version}` where `{file_version}` - the version value saved at step 5 |  |
| 7. List the storage with details displaying | `pipe storage ls cp://{storage_name} -l` | The file `{file_name}` with `Size` value equals to the size of the file uploaded at step 1 is displayed |
