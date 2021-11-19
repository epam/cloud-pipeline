# MV operation with files: absolute paths without filename

**Actions**:

1.	Create 2 storages (via pipe cli to register in the base) [`pipe storage create --path storage_name...`]
2.	Create test file (`resources/test_file.txt`)
3.	Check that following cases work with absolute paths: 
    1.	Move test file to created storage specifying the absolute path to the folder (folder shall be created automatically) `[pipe storage mv ../resources/ {test_case} /test_file.txt cp://storage_name/]`
    2.	Move test file back to the local machine but to the another folder specifying the absolute path to the folder (folder shall be created automatically) `[pipe storage mv cp://storage_name/new_folder/test_file.txt ..../resources/new_folder/]`
    3.	Check that file has been moved correctly (file exists, date and size are correct)
    4.	Move test file to another created storage specifying the absolute paths to the folder `[pipe storage mv cp://storage_name/new_folder/test_file.txt cp://other_storage_name/new_folder/]`
    5.	Check that file has been moved correctly (file exists, date and size are correct)
4.	Delete storages

***
**Expected result:**

*1.*	Folder `new_folder` has been created in storage `storage_name`. Created folder includes `test_file.txt` file and it's correct (at the same time it has been removed from local machine)

*3.*	Folder `new_folder` has been created on local machine. Created folder includes `test_file.txt` file and it's correct (at the same time it has been removed from storage `storage_name`)

*5.*	Folder `new_folder` has been created in storage `other_storage_name`. Created folder includes `test_file.txt` file and it's correct (at the same time it has been removed from storage `storage_name`)