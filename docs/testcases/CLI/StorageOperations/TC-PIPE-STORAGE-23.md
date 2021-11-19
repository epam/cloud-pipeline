# CP operation with role model

**Requirements:** 

TestUser1 with permission to create data storage (Owner).

TestUser2.

**Actions**:
1.	Create 2 storages as Owner (TestUser1) (via pipe cli to register in the base) `[pipe storage save ...]`
2.	Create test file (`resources/test_file.txt`)
3.	Upload test file `test_file.txt` to created storage `[pipe storage cp /resources/test_file.txt cp://storage_name/test_file.txt]`.
4.	Login as TestUser2 that doesn't have READ and WRITE permissions on created storage 
5.	Check that TestUser2 can't do any of the following actions:
    1.	Try to copy test file from storage to local machine. Error message should be returned.
    2.	Try to copy test file from local machine to storage. Error message should be returned.
6.	Login as TestUser1 (Owner)
7.	Give READ permission on created storage to TestUser2
8.	Login as TestUser2
9.	Check that TestUser2 can't wtite something to storage but can copy file from the storage
    1.	Try to copy test file from storage to local machine. File should be copied correctly. 
    2.	Try to copy test file from local machine to storage. Error message should be returned.
10.	Login as TestUser1 (Owner)
11.	Delete storages.

***
**Expected result:**

1.	If TestUser2 doesn't have READ and WRITE permissions then error message is returned.
2.	If TestUser2 has only READ permission then file is copied correctly and error message is returned when user try to write file to storage. 