# CP operation with folders: --exclude files

**Actions**:
1.	Create 2 storages (`storage_name` and `other_storage_name`) `[pipe storage save ...]`
2.	Create test folder (`resources/`). Put files `test_file.txt`, `test_file.json` to it and create folder `/new/test_file.txt` here.
3.	Check that upload files to storage works correctly with `--exclude` option:  
    1.	Upload test folder to storage `[pipe storage cp ../resources/ cp://storage_name/test_folder/ --recursive --exclude "*.json" --exclude "new/*"]`
    2.	Check that only `test_file.txt` file is uploaded to storage.
4.	Upload whole test folder `resources/` to storage 
5.	Check that upload files from storage works correctly with `--exclude` option: 
    1.	Upload folder to local machine `[pipe storage cp cp://storage_name/test_folder/ ... --recursive --exclude "*.json" --exclude "new/*"]`
    2.	Check that only `test_file.txt` file is uploaded to local machine.
6.	Copy files from storage to another storage using `--exclude` option: 
    1.	`[pipe storage cp cp://storage_name/test_folder/ cp://other_storage_name/test_folder_copy/ --recursive --exclude "*.json" --exclude "new/*"]`
    2.	Check that only `cp://storage_name/test_folder_copy/test_file.txt` is uploaded to `other_storage_name`
7.	Delete storages.

***
**Expected result:**

*3.*	Only `test_file.txt` file from folder `/resources` is uploaded to storage.

*5.*	Only `test_file.txt` file from folder `/resources` is uploaded to local machine. 

*6.*	Only `test_file.txt` file from folder `/resources` is uploaded to `other_storage_name`.