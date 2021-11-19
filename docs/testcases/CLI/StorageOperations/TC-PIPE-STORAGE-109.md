# [CLI] CP: copy folder between storages with skip existing option (negative)

**Actions**:
1.  Create storages `storage_name1` and `storage_name2`
2.  Create folder on storage: `cp://storage_name1/folder` and put file `file_name` into
3.  Put file `file_name` to storage: `cp://storage_name2/folder/file_name` (create with content different from `cp://storage_name1/folder/file_name`)
4.	Call `pipe with --skip-existing option: pipe storage cp cp://storage_name1/folder cp://storage_name2/folder -r -s -f`
5.  Delete storages

***
**Expected result:**

3.	File `cp://storage_name2/folder/file_nam`e successfully created
4.	File `cp://storage_name2/folder/file_name` should be overwritten