# MV operation with folders: --exclude files

**Actions**:
1.	Create 2 storages (`storage_name` and `other_storage_name`) `[pipe storage create ...]`
2.	Create test folder (`resources/`). Put files `test_file.txt`, `test_file.json` to it and create folder `/new/test_file.txt` here.
3.	Check that move files to storage works correctly with `--exclude` option:  
    1.	Move test folder to storage `[pipe storage mv ../resources/ cp://storage_name/test_folder/ --recursive --exclude "*.json" --exclude "new/*"]`
    2.	Check that only `test_file.txt` file is moved to storage.
4.	Move whole test folder `resources/` to storage 
5.	Check that move files from storage works correctly with `--exclude` option: 
    1.	Move folder to local machine `[pipe storage mv cp://storage_name/test_folder/ ... --recursive --exclude "*.json" --exclude "new/*"]`
    2.	Check that only `test_file.txt` file is moved to local machine.
6.	Move files from storage to another storage using `--exclude` option: 
    1.	`[pipe storage mv cp://storage_name/test_folder/ cp://other_storage_name/test_folder_copy/ --recursive --exclude "*.json" --exclude "new/*"]`
    2.	Check that only `cp://storage_name/test_folder_copy/test_file.txt` is moved to `other_storage_name`
7.	Delete storages.

***
**Expected result:**

*3.*	Only `test_file.txt` file from folder `/resources` is uploaded to storage (at the same time only `test_file.txt` file has been removed from local machine).

*5.*	Only `test_file.txt` file from folder `/resources` is uploaded to local machine (at the same time only `test_file.txt` file has been removed from storage). 

*6.*	Only `test_file.txt` file from folder `/resources` is uploaded to `other_storage_name` (at the same time only `test_file.txt` file has been removed from `storage_name`).