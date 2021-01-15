# [CLI] Copy file from storage to local relative path

**Actions**:
1.  Create storage
2.	Copy `file_name.txt` file to storage: `cp source cp://storage_name/file_name.txt`
3.	Copy file from storage: `pipe storage cp cp://storage_name/file_name.txt file_name.txt`
4.  Delete storage

***
**Expected result:**

2.	File is uploaded successfully
3.	File is downloaded successfully