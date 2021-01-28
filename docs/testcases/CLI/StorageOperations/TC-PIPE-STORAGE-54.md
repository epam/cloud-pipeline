# Create directory by CLI validation

**Requirements:**
Create storage.

**Actions**:
1.	Call: `pipe storage mkdir cp://{storage_name}/{new_folder_name}`
2.	Call: `pipe storage ls cp://{storage_name}`
3.	Repeat step 1
4.	Call: `pipe storage mkdir cp://{storage_name}/{new_folder_name}` in upper case
5.	Call: `pipe storage mkdir cp://{storage_name}/{new_folder_name}` in lower case
6.	Call: `pipe storage ls cp://{storage_name}`

***
**Expected result:**

1.	The command runs without errors.
2.	Directory {`new_folder_name`} is shown in list.
3.	Message `"Folder already exist"` is printed.
4.	The command runs without errors.
5.	The command runs without errors.
6.	{`new_folder_name`} is shown in upper case and {`new_folder_name`} is shown in lower case.