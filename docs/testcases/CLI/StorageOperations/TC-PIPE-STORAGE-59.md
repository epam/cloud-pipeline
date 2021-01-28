# CP operation with similar file keys

**Actions**:
1.  Create storage
2.	Upload `test_file.txt` to created storage
3.	Copy `test_file` file to storage: `pipe storage cp test_file cp://storage_NAME/test_file`
4.	Delete `test_file` from storage
5.	Delete `test_file.txt` from storage
6.  Delete storage

***
**Expected result:**

2.	File `file_name.txt` is successfully uploaded
3.	File `file_name` is successfully uploaded
4.	File `file_name` is successfully deleted
5.	File `file_name.txt` is successfully deleted