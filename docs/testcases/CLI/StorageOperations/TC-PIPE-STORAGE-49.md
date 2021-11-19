# RM operation delete non existing file/folder

**Actions**:
1.	Create storage `storage-name` `[pipe storage save ...]`
2.	Call `rm` command for any file on empty storage `[pipe storage rm --yes cp://storage-name/do-not-exist.txt]`
3.	Upload test file `test_file1.txt` and test folder `test_folder/test_file2.txt` to storage 
4.	Call `rm` command for non-existing file on storage `[pipe storage rm --yes cp://storage-name/do-not-exist.txt]`
5.	Call `rm` command for non-existing folder on storage `[pipe storage rm --yes cp://storage-name/do-not-exist/]`

***
**Expected result:**

Command should fail and print appropriate error message