# [CLI] MV: copy file between storages with skip existing option (negative)

**Actions**:
1.  Create storages `storage_name1` and `storage_name2`
2.  Create file on storage: `cp://storage_name1/file_name`
3.  Put file `file_name` to storage: `cp://storage_name2/file_name` (create with content different from `cp://storage_name1/file_name`)
4.	Call pipe with --skip-existing option: `pipe storage mv cp://storage_name1/file_name cp://storage_name2/file_name -s -f`
5.  Delete storages

***
**Expected result:**

3.	File `cp://storage_name2/folder/file_name` is successfully created
4.	File `cp://storage_name2/folder/file_name` should be overwritten (file `cp://storage_name1/folder/file_name` must be deleted)