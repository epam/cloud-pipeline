# Ls operation for non existing file/folder

**Actions**:
1.  Create storage `[pipe storage create --name {storage_name}...]`
2.	Put test file `test_file1.txt` and folder `test_folder/test_file2.txt` to created storage
3.  Perform command `ls` for non-existent file on the storage `[pipe storage ls cp://storage-name/do-not-exist.txt]`
4.  Perform command `ls` for non-existent folder on the storage `[pipe storage ls cp://storage-name/do-not-exist/]`
5.  Delete storage.

***
**Expected result:**

Command should return exit code '0' and empty list.