# CP operation with folders: absolute paths

**Actions**:

1.	Create 2 storages (via pipe cli to register in the base) [`pipe storage create --path storage_name...`]
2.	Create folder (`resources/`)
3.	Check that following cases work with absolute paths and `--recursive` option (with `/` at the end of the path): 
    1.	Put test folder to created storage specifying the absolute path `[pipe storage cp ../resources/ cp://storage_name/test_folder/ --recursive]`. 
    2.	Download the folder back to the local machine specifying the absolute path `[pipe storage cp cp://storage_name/test_folder/ .../resources/ --recursive]`. 
    3.	Check that files are copied correctly (size, md5sum).
    4.	Copy folder to the other storage using the absolute paths `[pipe storage cp cp://storage_name/test_folder/ cp://other_storage_name/test_folder_copy/ --recursive]`
    5.	Check that files are copied correctly (size, md5sum).
4.	Delete storages.

***
**Expected result:**

*1.*	Files appear on the storage.

*3.*	Files have been downloaded on the local machine.

*5.*	Files have been copied to the `other_storage_name` storage.