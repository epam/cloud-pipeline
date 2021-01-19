# MV operation with folders: --include and --exclude options combination

**Actions**:
1.	Create 2 storages `[pipe storage create ...]`
2.	Create folder (`resources/`). Put files `test_file.txt`, `test_file.json` and folder `/new/test_file.txt` into
3.	Check that uploading files to storage works correctly with `--include` and `--exclude` options: 
    1.	Upload folder to created storage `[pipe storage mv ../resources/ cp://storage_name/test_folder/ --recursive --include "*.txt" --exclude "new/*"]`
    2.	Check that only `test_file.txt` is uploaded to storage
4.	Upload whole folder `resources/` to created storage
5.	Check that uploading files from storage works correctly with `--include` and `--exclude` options:  
    1.	Move folder to local machine `[pipe storage mv cp://storage_name/test_folder/ ... --recursive --include "*.txt" --exclude "new/*"]`
    2.	Check that only `test_file.txt` is uploaded 
6.	Move files to `other_storage_name` using `--include` and `--exclude` options: 
    1.	`[pipe storage mv cp://storage_name/test_folder/ cp://other_storage_name/test_folder_copy/ --recursive --include "*.txt" --exclude "new/*"]`
    2.	Check that only `cp://storage_name/test_folder_copy/test_file.txt` is moved 
7.	Delete storages

***
**Expected result:**

1.	Only `test_file.txt` is uploaded to storage.
2.	Only `test_file.txt` is uploaded to local machine.
3.	Only `test_file.txt` is uploaded to storage `other_storage_name`.