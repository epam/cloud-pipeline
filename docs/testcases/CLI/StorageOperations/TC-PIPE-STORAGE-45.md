# RM operation with role model

**Requirements:**
Owner user and other TestUser

**Actions**:
1.	Create storage `storage_name` as Owner (via pipe cli to register in the base) `[pipe storage save ...]`
2.	Create test file (`resources/test_file.txt`)
3.	Upload test file `test_file.txt` to created storage `[pipe storage cp /resources/test_file.txt cp://storage_name/test_file.txt]`.
4.	Login as TestUser that doesn't have READ and WRITE permissions for storage `storage_name`.
5.	Check that TestUser can't perform any actions: 
    1.	Try delete file `test_file.txt` from storage cp://storage_name/test_file.txt. Error message should be returned.
6.	Login as Owner
7.	Give READ permission on `storage_name` storage for TestUser
8.	Login as TestUser
9.	Check that TestUser can't perform any actions: 
    1.	Try delete file `test_file.txt` from storage cp://storage_name/test_file.txt. Error message should be returned.
10.	Login as Owner
11.	Give WRITE permission on `storage_name` storage for TestUser
12.	Login as TestUser
13.	Check that TestUser can perform any actions: 
    1.	Delete file `test_file.txt` from storage `cp://storage_name/test_file.txt`.
14.	Login as Owner
15.	Delete storage

***
**Expected result:**

1.	If user doesn't have READ and WRITE permissions then error message is returned.
2.	If user has READ and WRITE permissions then command is performed successfull.