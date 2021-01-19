# [CLI] CP: copy between storage folder with slash - should copy content only

**Actions**:
1.  Create storages storage_name1 and storage_name2
2.  Create file in folder: `folder/file` to storage: `cp://storage_name1/folder/`
3.  Call: `pipe storage cp cp://storage_name1/folder/ cp://storage_name2/folder/ -r`
4.	Call: `pipe storage cp cp://storage_name1/folder cp://storage_name2/folder/-r`
5.	Call: `pipe storage cp cp://storage_name1/folder cp://storage_name2/folder/ -r`
6.	Call: `pipe storage cp cp://storage_name1/folder/ cp://storage_name2/folder/ -r`
7.  Delete storage

***
**Expected result:**

Folder should be successfully moved: folder `cp://storage_name2/folder/` created