# CP operation with folders: path with home directory (~/) 

**Actions**:

1.	Create storage (via pipe cli to register in the base) `[pipe storage save ...]`
2.	Create test folder (`~/resources/`)
3.	Check that following cases work with home directory path, `--recursive` option and with `/`: 
    1.	Put test folder to created storage `[pipe storage cp ~/resources/ cp://storage_name/test_folder/ --recursive]`. 
    2.	Download the folder back to the local machine home directory  `[pipe storage cp cp://storage_name/test_folder/ ~/resources/tmp/ --recursive]`. 
    3.	Check that files have been copied correctly (size, md5sum)
4.	Check that following cases work with home directory path, `--recursive` option and without `/`:  
    1.	Put test folder to created storage `[pipe storage cp ~/resources cp://storage_name/test_folder --recursive]`. 
    2.	Download the folder back to the local machine home directory  `[pipe storage cp cp://storage_name/test_folder ~/resources/tmp --recursive]`. 
    3.	Check that files have been copied correctly (size, md5sum)
5.	Delete storage.

***
**Expected result:**

*1.*	Folder `test_folder/` has been created on the storage.
*3.*	Folder `test_folder/` has been downloaded from storage to home directory.