# CP operation with files: absolute paths

**Actions**:

1.	Create 2 storages (via pipe cli to register in the base) [`pipe storage save --path storage_name...`]
2.	Create test file (`resources/test_file.txt`)
3.	Check that following cases work with absolute paths: 
    1.	Put test file to created storage1 specifying the absolute path `[pipe storage cp ../resources/ {test_case} /test_file.txt cp://storage_name/test.txt]`
    2.	Download test file back to the local machine specifying the absolute path `[pipe storage cp cp://storage_name/test.txt .../resources/test.txt]`
    3.	Check that file has been copied correctly (file exists, date and size are correct)
    4.	Copy test file to created storage2 specifying the absolute paths `[pipe storage cp cp://storage_name/test.txt cp://other_storage_name/test_file.txt]`
    5.	Check that file has been copied correctly (file exists, date and size are correct)
4.	Delete storages

***
**Expected result:**

*1.*	File appears in storage `storage_name`

*3.*	File has been downloaded from storage to local machine

*5.*	File has been copied to storage `other_storage_name`