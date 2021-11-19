# MV operation with folders: --include files

**Actions**:
1.	Create 2 storages (via pipe cli to register in the base) `[pipe storage create ...]`
2.	Create folder (`resources/`). Put files `test_file.txt`, `test_file.json` and folder `/new/test_file.txt` into
3.	Check that uploading files to storage works correctly with `--include` option: 
    1.	Move folder to created storage `[pipe storage mv ../resources/ cp://storage_name/test_folder/ --recursive --include "*.json" --include "new/*"]`
    2.	Check that only `test_file.txt` isn't moved to storage
4.	Move whole folder `resources/` to created storage
5.	Check that uploading files from storage works correctly with `--include` option:  
    1.	Move folder to local machine `[pipe storage mv cp://storage_name/test_folder/ ... --recursive --include "*.json" --include "new/*"]`
    2.	Check that only `test_file.txt` isn't moved 
6.	Move files to `other_storage_name` using `--include` option 
    1.	`[pipe storage mv cp://storage_name/test_folder/ cp://other_storage_name/test_folder_copy/ --recursive --include "*.json" --include "new/*"]`
    2.	Check that only `cp://storage_name/test_folder_copy/test_file.txt` isn't moved to storage 
7.	Delete storages

***
**Expected result:**

1.	Only `test_file.txt` isn't uploaded to storage from folder `/resources`.
2.	Only `test_file.txt` isn't uploaded to local machine from folder `/resources`.
3.	Only `test_file.txt` isn't uploaded to storage from folder `/resources`.