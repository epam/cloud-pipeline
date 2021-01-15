# [CLI] MV: upload folder with skip existing option

**Actions**:
1.  Create storage
2.  Upload file to storage: `pipe storage mv file_name cp://storage_name/folder/file_name`
3.	Create folder with uploaded file `file_name`
4.	Upload folder to storage: `pipe storage mv folder/ cp://storage_name/folder/ -s -f -r`
5.  Delete storage

***
**Expected result:**

2.	File is uploaded successfully
3.	Folder is created successfully
4.	File `file_name` is skipped