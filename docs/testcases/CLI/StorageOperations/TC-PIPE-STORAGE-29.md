# CP operation copy file to non existing storage

**Actions**:

1.	Create test file on local machine (`resources/test_file.txt`)
2.	Call `[pipe storage cp absolute_path_to_file cp://unexisting_storage]`
3.	Call `[pipe storage cp cp://unexisting_storage cp://existing_storage]`
4.	Call `[pipe storage cp cp://existing_storage cp://unexisting_storage]`

***
**Expected result:**

Command should fail and print appropriate error message