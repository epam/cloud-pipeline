# [Negative] Negative tests for mvtodir command

**Actions**:
1.	Move storage to unexciting directory: `pipe storage mvtodir <data storage name> <non existing folder>`
2.	Move unexciting storage to exciting directory: `pipe storage mvtodir <non existing data storage> <folder name>`
3.	Move unexciting storage to unexciting directory: `pipe storage mvtodir <non existing data storage> <non existing folder>`

***
**Expected result:**

1.	Throw error `"Directory with name {unexciting_directory_name} does not exist!"`
2.	Throw error `"Error: Datastorage with name {unexciting_storage_name} does not exist!"`
3.	Throw error `"Directory with name {unexciting_directory_name} does not exist!"`