# Validation of file versions

| Action | `pipe` command | Expected result |
|---|---|---|
| 1. Upload some file to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 2. Upload the file with the same name as at step 1 but with another size to the existing storage | `pipe storage cp ./{file_name} cp://{storage_name}/{file_name}` |  |
| 3. List the storage with details displaying | `pipe storage ls cp://{storage_name} -l` | The single record about the file `{file_name}` is displayed. The size of the file is the same as it was at step 2 |
| 4. List the storage with versions and details displaying | `pipe storage ls cp://{storage_name} -v` | There are 3 records about the file `{file_name}` are displayed:<br />- record with empty `Version` value and `Size` value equals to the size of the file uploaded at step 2<br />- record with non-empty `Version` value and the mark `(latest)`, `Size` value equals to the size of the file uploaded at step 2<br />- record with non-empty `Version` value, `Size` value equals to the size of the file uploaded at step 1 |
