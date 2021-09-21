# CP operations: non existing file/folder

**Actions**:
1.	Call `[pipe storage cp non_existing_file cp://existing_storage]`
2.	Call `[pipe storage cp cp://existing_storage non_existing_file]`
3.	Call `[pipe storage cp non_existing_folder cp://existing_storage]`
4.	Call `[pipe storage cp cp://existing_storage non_existing_folder]`

***
**Expected result:**

Command should fail and print appropriate error message.