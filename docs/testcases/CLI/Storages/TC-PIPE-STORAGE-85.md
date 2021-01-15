# [CLI] MV: upload folder with slash - should upload content only

**Actions**:
1.  Create storage
2.  Create file in folder: `folder/file` 
3.	Upload folder to storage: `pipe storage mv folder/ cp://storage_name/folder/ -r`
4.	Upload folder to storage: `pipe storage mv folder/ cp://storage_name/folder -r`
5.	Upload folder to storage: `pipe storage mv folder cp://storage_name/folder -r`
6.	Upload folder to storage: `pipe storage mv folder cp://storage_name/folder/ -r`
7.  Delete storage

***
**Expected result:**

Folder should be uploaded successfully: file `cp://storage_name/file` is created.