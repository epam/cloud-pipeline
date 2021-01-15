# CP operation with folders: relative paths

**Actions**:

1.	Create storage (via pipe cli to register in the base) `[pipe storage save ...]`
2.	Create test folder (`resources/`)
3.	Check that following cases work with relative paths: 
    1.	Put test folder to created storage specifying the relative path to the folder `[pipe storage cp ./resources/ cp://storage_name/test_folder/ --recursive]`. 
    2.	Download test folder `test_folder/` back to the local machine specifying the relative path `[pipe storage cp cp://storage_name/test_folder/ ./resources/tmp/ --recursive]`. 
    3.	Check that files have been copied correctly (size, md5sum)
    4.	Download test folder back to the local machine to the current folder `[pipe storage cp cp://storage_name/test_folder/ ./ --recursive]`
    5.	Check that files have been copied correctly (size, md5sum)
    6.	Download test folder `test_folder/` back to the local machine to the current folder `[pipe storage cp cp://storage_name/test_folder/ . --recursive]`
    7.	Check that files have been copied correctly (size, md5sum)
4.	Delete storage

***
**Expected result:**

*1.*	Folder `test_folder/` has been created on the storage.

*3, 5, 7.*	Folder `test_folder/` has been downloaded from storage to local machine and it's correct.