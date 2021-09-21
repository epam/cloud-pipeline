# CP operation with files: relative paths

**Actions**:

1.	Create storage (via pipe cli to register in the base) `[pipe storage save ...]`
2.	Create test file (`resources/test_file.txt`)
3.	Check that following cases work with relative paths: 
    1.	Put test file to created storage specifying the relative path to the file `[pipe storage cp ./resources/test_file.txt cp://storage_name/]`. 
    2.	Download test file back to the local machine specifying the relative path `[pipe storage cp cp://storage_name/new_folder/test_file.txt ./resources/test.txt]`. 
    3.	Check that file has been copied correctly (size, md5sum)
    4.	Download test file back to the local machine to the current folder `[pipe storage cp cp://storage_name/new_folder/test_file.txt ./]`
    5.	Check that file has been copied correctly (size, md5sum)
    6.	Download test file back to the local machine to the current folder `[pipe storage cp cp://storage_name/new_folder/test_file.txt .]`
    7.	Check that file has been copied correctly (size, md5sum)
4.	Delete storage

***
**Expected result:**

*1.*	File `test_file.txt` has been created on storage `storage_name`.

*3, 5, 7.*	File `test_file.txt` has been downloaded from storage to local machine and it's correct.