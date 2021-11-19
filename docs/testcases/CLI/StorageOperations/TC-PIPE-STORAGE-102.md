# [CLI] CP: download folder with slash - should download content only

**Actions**:
1.  Create storage
2.  Create file in folder: `folder/file` and upload folder to storage: `cp://storage_name/folder/`
3.  Download folder from storage: `pipe storage cp cp://storage_name/folder/ new_folder/ -r`
4.	Download folder from storage: `pipe storage cp cp://storage_name/folder new_folder/ -r`
5.	Download folder from storage: `pipe storage cp cp://storage_name/folder new_folder -r`
6.	Download folder from storage: `pipe storage cp cp://storage_name/folder/ new_folder -r`
7.  Delete storage

***
**Expected result:**

Folder should be successfully downloaded: file `new_folder/file` is created