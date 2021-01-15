# CP operation with files: path with home directory (~/) 

**Actions**:

1.	Create storage (via pipe cli to register in the base) [`pipe storage save --path storage_name...`]
2.	Create test file (`~/resources/test_file.txt`).
3.	Check that following cases work with home directory path:
    1.	Put test file to created storage specifying relative path to file `[pipe storage cp ~/resources/test_file.txt cp://storage_name/]`.
    2.	Download file back to the local machine home directory `[pipe storage cp cp://storage_name/test_file.txt ~/resources/test.txt]`. 
    3.	Check that file has been copied correctly (file exists, date and size are correct).
4.	Delete storage.

***
**Expected result:**

*1.*	File `test_file.txt` has been created in storage and it's correct.
*3.*	File `test_file.txt` has been copied to the local machine home directory and it's correct.