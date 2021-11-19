# [CLI] upload new file in not empty folder

**Actions**:
1.	Create storage
2.	Create folder in storage `{folder_name}`
3.	Create file in folder
4.	Upload new file in the folder, file shall have unique name (`pipe storage cp {path_to_ file} cp://{\storage_name}/{folder_name}`)
5.  Delete storage

***
**Expected result:**

File is uploaded, force flag isn't required.