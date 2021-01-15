# CP operation with files: absolute paths without filename specified

**Actions**:

1.	Create 2 storages (via pipe cli to register in the base) [`pipe storage save --path storage_name...`]
2.	Create test file (`resources/test_file.txt`)
3.	Check that following cases work with absolute paths: 
    1.	Put test file to created storage specifying the absolute path to the folder (folder shall be created automatically) `[pipe storage cp absolute_path_to_file cp://storage_name/new_folder/]`
    2.	Download test file back to the machine but to the another folder specifying the absolute path to the folder (folder shall be created automatically) without file name `[pipe storage cp cp://storage_name/new_folder/test_file.txt ..../resources/new_folder/]`
    3.	Check that folder has been created and file has been copied correctly (file exists, date and size are correct)
    4.	Copy test file to other created storage specifying the absolute paths `[pipe storage cp cp://storage_name/new_folder/test_file.txt cp://other_storage_name/new_folder/]`
    5.	Check that folder has been created and file has been copied correctly (file exists, date and size are correct)
4.	Delete storages

***
**Expected result:**

*1.*	Folder `new_folder` is created in storage `storage_name`. Created folder includes `test_file.txt` file and it's correct.
*3.*	Folder `new_folder` is created on local machine. Created folder includes `test_file.txt` file and it's correct.
*5.*	Folder `new_folder` is created in storage `other_storage_name`. Created folder includes `test_file.txt` file and it's correct.