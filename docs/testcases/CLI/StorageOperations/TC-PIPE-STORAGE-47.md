# RM operation with files

**Actions**:
1.	Create storage
2.	Create test file `test.txt`
3.	Put `test.txt` file to storage `cp://storage_name/test.txt`
4.	Delete `test.txt` file from storage `[pipe storage rm cp://storage_name/test.txt]`
5.	Check that file `test.txt` is deleted
6.	Delete storage

***
**Expected result:**

File has been deleted from storage.