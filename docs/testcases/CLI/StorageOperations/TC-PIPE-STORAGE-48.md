# RM operation with folders: --exclude and --include options

**Actions**:
1.	Create storage `[pipe storage save ...]`
2.	Create folder (`resources/`). Put files `test_file.txt`, `test_file.json` and folder `/new/` with files (`/new/test_file.txt`, `/new/test_file.json`) into
3.	Upload folder `resources/` to created storage `cp://storage_name/new_folder/`
4.  Check that removing files from storage works correctly with `--exclude` option: 
    1.	Remove files from storage `[pipe storage rm cp://storage_name/test_folder/ --recursive --exclude "*.json" --exclude "new/*"]`
    2.	Check that only `test_file.txt` is removed from storage.
5.	Upload file `test_file.txt` to created storage.
6.	Check that removing files from storage works correctly with `--include` option:  
    1.	Remove files from storage `[pipe storage rm cp://storage_name/test_folder/ --recursive --include "*.json" --include "new/*"]`.
    2.	Check that only `test_file.txt` isn't removed from storage.
7.	Upload folder `resources/` to created storage `cp://storage_name/new_folder/` again. 
    1.	Remove files from storage `[pipe storage rm cp://storage_name/test_folder/ --recursive --include "new/*" --exclude "*/*.json"]`
    2.	Check that only `cp://storage_name/new_folder/test_file.txt` is removed from storage.
7.	Delete storage

***
**Expected result:**

1.	If only `--exclude` option is used: only `test_file.txt` file is removed.
2.	If only `--include` option is used: only `test_file.txt` file isn't removed.
3.	If `--exclude` and `--include` options are used: only `new_folder/test_file.txt` file is removed.