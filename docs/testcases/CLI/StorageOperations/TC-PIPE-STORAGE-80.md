# [CLI] CP: upload folders with the same keys

**Actions**:
1.  Create storage
2.	Create local folders: `test` and `test_folder`
3.	Upload `test` folder to storage: `pipe storage cp test/ cp://storage_name/test -r`
4.  Delete storage

***
**Expected result:**

Only `test` folder is uploaded.