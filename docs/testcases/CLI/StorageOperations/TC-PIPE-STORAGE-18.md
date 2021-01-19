# MV operation with folders: relative paths

**Actions**:

1.	Create storage (via pipe cli to register in the base) `[pipe storage create ...]`
2.	Create test folder (`resources/`)
3.	Check that following cases work with relative paths: 
    1.	Move test folder to created storage specifying the relative path to the folder `[pipe storage mv ./resources/ cp://storage_name/test_folder/ --recursive]`. 
    2.	Move test folder `test_folder/` back to the local machine specifying the relative path `[pipe storage mv cp://storage_name/test_folder/ ./resources/tmp/ --recursive]`. 
    3.	Check that files have been moved correctly (size, md5sum)
    4.	Move test folder back to the local machine to the current folder `[pipe storage mv cp://storage_name/test_folder/ ./ --recursive]`
    5.	Check that files have been moved correctly (size, md5sum)
    6.	Move test folder `test_folder/` back to the local machine to the current folder `[pipe storage mv cp://storage_name/test_folder/ . --recursive]`
    7.	Check that files have been moved correctly (size, md5sum)
4.	Delete storage

***
**Expected result:**

*1.*	Folder `test_folder/` has been created on the storage (at the same time it has been removed from local machine).

*3, 5, 7.*	Folder `test_folder/` has been downloaded from storage to local machine (at the same time it has been removed from storage `storage_name`).