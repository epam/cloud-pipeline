# MV operation with files: absolute paths

**Actions**:

1.	Create 2 storages (via pipe cli to register in the base) [`pipe storage create --path storage_name...`]
2.	Create test file (`resources/test_file.txt`)
3.	Check that following cases work with absolute paths: 
    1.	Move test file to created storage1 specifying the absolute path `[pipe storage mv ../resources/ {test_case} /test_file.txt cp://storage_name/test.txt]`
    2.	Move test file back to the machine specifying the absolute path `[pipe storage mv cp://storage_name/test.txt .../resources/test.txt]`
    3.	Check that file has been moved correctly (file exists, date and size are correct)
    4.	Move test file to created storage2 specifying the absolute paths `[pipe storage mv cp://storage_name/test.txt cp://other_storage_name/test_file.txt]`
    5.	Check that file has been moved correctly (file exists, date and size are correct)
4.	Delete storages

***
**Expected result:**

*1.*	File appears on storage1 (at the same time it has been removed from local machine)

*3.*	File has been downloaded from storage (at the same time it has been removed from storage)

*5.*	File has been copied to another storage (at the same time it has been removed from storage1)