# MV operation with files: path with home directory (~/) 

**Actions**:

1.	Create storage (via pipe cli to register in the base) [`pipe storage save --path storage_name...`]
2.	Create test file (`~/resources/test_file.txt`).
3.	Check that following cases work with home directory path:
    1.	Move test file to created storage specifying the relative path to file  `[pipe storage mv ~/resources/test_file.txt cp://storage_name/]`.
    2.	Move file back to the local machine home directory `[pipe storage mv cp://storage_name/test_file.txt ~/resources/test.txt]`. 
    3.	Check that file has been moved correctly (file exists, size, md5sum).
4.	Delete storage.

***
**Expected result:**

*1.*	File `test_file.txt` has been created in storage and it's correct (at the same time it has been removed from local machine).

*3.*	File `test_file.txt` has been downloaded to the local machine home directory and it's correct (at the same time it has been removed from storage). 