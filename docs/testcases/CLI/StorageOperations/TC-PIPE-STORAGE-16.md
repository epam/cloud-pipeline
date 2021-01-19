# MV operation with folders: path with home directory (~/)

**Actions**:

1.	Create storage (via pipe cli to register in the base) `[pipe storage save ...]`
2.	Create test folder (`~/resources/`)
3.	Check that following cases work with home directory path, `--recursive` option and with `/`: 
    1.	Move test folder to created storage `[pipe storage mv ~/resources/ cp://storage_name/test_folder/ --recursive]`. 
    2.	Move the folder back to the local machine home directory `[pipe storage mv cp://storage_name/test_folder/ ~/resources/tmp/ --recursive]`. 
    3.	Check that files have been moved correctly (size, md5sum)
4.	Check that following cases work with home directory path, `--recursive` option and without `/`:  
    1.	Move test folder to created storage `[pipe storage mv ~/resources cp://storage_name/test_folder --recursive]`. 
    2.	Move the folder back to the local machine home directory  `[pipe storage mv cp://storage_name/test_folder ~/resources/tmp --recursive]`. 
    3.	Check that files have been moved correctly (size, md5sum)
5.	Delete storage.

***
**Expected result:**

*1.*	Folder `test_folder/` has been created on the storage (at the same time it have been removed from local machine).
*3.*	Folder `test_folder/` has been downloaded from storage to home directory (at the same time it have been removed from storage).