# LS operation with role model

**Requirements:**
Admin user and other TestUser

**Actions**:
1.	Create storage `storage_name` as Owner `[pipe storage save --name {storage_name} --path {storage_name} --onCloud]`
2.	Create test file (`resources/test_file.txt`)
3.	Upload test file `test_file.txt` to created storage `[pipe storage cp /resources/test_file.txt cp://storage_name/test_file.txt]`.
4.	Login as TestUser that doesn't have READ and WRITE permissions for storage `storage_name`. 
5. Perform `ls` command `[pipe storage ls cp://storage_name/]`. Check error message.
6.	Login as Admin. Give READ permission on `storage_name` storage for TestUser: `[pipe set-acl storage_name -t data_storage -s {user_name} -a r]`
    1. Login as TestUser. Perform `ls` command [pipe storage ls cp://storage_name/] and get list of files on the storage.
7.  Login as Admin. Give only WRITE permission on `storage_name` storage for TestUser: `[pipe set-acl storage_name -t data_storage -s {user_name} -a w -d r]`
    1.	Perform `ls` command `[pipe storage ls cp://storage_name/]`. Check error message.
7.	Delete storage

***
**Expected result:**

1.	If user doesn't have READ permission then error message is returned.
2.	If user has READ permission then command is performed successfull.