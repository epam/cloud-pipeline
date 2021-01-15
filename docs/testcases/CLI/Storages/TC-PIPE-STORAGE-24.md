# MV operation with role model

**Requirements:** 

TestUser1 with admin permissions.

TestUser2.

**Actions**:
1.	Create 2 storages (`storage` and `other_storage`) as admin `[pipe storage create --name {storage_name} --path {storage_name} --onCloud]`
2.	Create test file (`resources/test_file.txt`)
3.	Upload test file `test_file.txt` to created storage `[pipe storage cp /resources/test_file.txt cp://storage_name/test_file.txt]`.
4.	Login as TestUser2 that doesn't have READ and WRITE permissions on created storage `storage`
5.	Check that TestUser2 can't do any of the following actions: 
    1.	Try to move test file from `storage` to local machine. Error message should be returned. 
    2.	Try to move test file from local machine to `storage`. Error message should be returned. 
    3.	Try to move test file from `storage` to `other_storage`. Error message should be returned.
6.	Give READ permission on `storage` for TestUser2: 
    1.	Try to move test file from storage to local machine. Error message should be returned.
    2.	Try to move test file from `storage` to `other_storage`. Error message should be returned.
7.	Give WRITE permission on `storage` for TestUser2: 
    1.	Try to move test file from `storage` to `other_storage`. Error message should be returned.
    2.	Try to move test file from local machine to `storage`. Check that file has been moved correctly. 
    3.	Try to move test file from `storage` to local machine. Check that file has been moved correctly.
8.	Give WRITE permission on `other_storage` for TestUser2: 
    1.	Try to move test file from `storage` to `other_storage`. Check that file has been moved correctly. 
9.	Delete storages.

***
**Expected result:**

1.	If TestUser2 doesn't have READ and WRITE permissions or has only READ permission then error message is returned.
2.	If TestUser2 has WRITE permission on both storages then file is moved correctly.  
3.	If TestUser2 has WRITE permission on `storage` but READ permission on `other-storage` then error message is returned.