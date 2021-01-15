# MV operation with folders: absolute paths

**Actions**:

1.	Create 2 storages (via pipe cli to register in the base) [`pipe storage create --path storage_name...`]
2.	Create folder (`resources/`)
3.	Check that following cases work with absolute paths and `--recursive` option (with `/` at the end of the path): 
    1.	Move test folder to created storage specifying the absolute path `[pipe storage mv ../resources/ cp://storage_name/test_folder/ --recursive]`. 
    2.	Move the folder back to the local machine specifying the absolute path `[pipe storage mv cp://storage_name/test_folder/ .../resources/ --recursive]`. 
    3.	Check that files are moved correctly (size, md5sum).
    4.	Move folder to the other storage using the absolute paths `[pipe storage mv cp://storage_name/test_folder/ cp://other_storage_name/test_folder_copy/ --recursive]`
    5.	Check that files are moved correctly (size, md5sum).
4.	Delete storages.

***
**Expected result:**

*1.*	Files appear on the storage (at the same time they have been removed from local machine).

*3.*	Files have been downloaded on the local machine (at the same time it has been removed from storage `storage_name`).

*5.*	Files have been moved to the `other_storage_name` storage (at the same time it has been removed from storage `storage_name`).