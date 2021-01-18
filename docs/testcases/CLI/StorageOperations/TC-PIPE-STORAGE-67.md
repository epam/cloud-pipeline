# [CLI] CP file in storage where folder with same name

**Actions**:
1.  Create storage `storage_name`
2.	Create folder `name1`.
3.  Create subfolder `name2`.
4.	Create local file that has name the same as folder from step 3
5.  Call `pipe storage cp {local_file_path\file_name} cp://{storage_name}/{name1}`
6.  Delete storage

***
**Expected result:**

3.	File is copied