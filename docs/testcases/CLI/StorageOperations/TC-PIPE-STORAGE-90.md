# [CLI] MV: upload folder with skip existing option (negative)

**Actions**:
1.  Create storage
2.  Upload file to storage: `pipe storage mv file_name cp://storage_name/folder/file_name`
3.	Modify file `file_name` (file size must be changed)
4.  Create folder with uploaded file `file_name`
5.	Upload folder to storage: `pipe storage mv folder/ cp://storage_name/folder/ -s -f -r`
5.  Delete storage

***
**Expected result:**

2.	File is uploaded successfully
3.	File size is midified successfully
4.  Folder is created successfully
5.	File `file_name` must not be skipped