# MV operation with files: relative paths

**Actions**:

1.	Create storage (via pipe cli to register in the base) `[pipe storage create ...]`
2.	Create test file (`resources/test_file.txt`)
3.	Check that following cases work with relative paths: 
    1.	Move test file to created storage specifying the relative path to the file `[pipe storage mv ./resources/test_file.txt cp://storage_name/]`. 
    2.	Move test file back to the local machine specifying the relative path `[pipe storage mv cp://storage_name/new_folder/test_file.txt ./resources/test.txt]`. 
    3.	Check that file has been moved correctly (size, md5sum)
    4.	Move test file back to the local machine to the current folder `[pipe storage mv cp://storage_name/new_folder/test_file.txt ./]`
    5.	Check that file has been moved correctly (size, md5sum)
    6.	Move test file back to the local machine to the current folder `[pipe storage mv cp://storage_name/new_folder/test_file.txt .]`
    7.	Check that file has been moved correctly (size, md5sum)
4.	Delete storage

***
**Expected result:**

*1.*	File `test_file.txt` has been created on storage `storage_name` (at the same time it has been removed from local machine).

*3, 5, 7.*	File `test_file.txt` has been downloaded from storage to local machine and it's correct (at the same time it has been removed from storage `storage_name`).