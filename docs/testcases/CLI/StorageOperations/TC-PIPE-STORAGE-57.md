# Check "-y" flag for "remove"

**Requirements:**
Create 2 files: file1_name and file2_name

**Actions**:
1.	Call: `pipe storage rm cp://{storage_name}/{file1_name}`
2.	Enter `N`. Call: `pipe storage ls`
3.	Call: `pipe storage rm cp://{storage_name}/{file1_name}`
4.	Enter `y`. Call: `pipe storage ls`
5.	Call: `pipe storage rm cp://{storage_name}/{file2_name} -y (--yes)`. Call: `pipe storage ls`

***
**Expected result:**

1.	Error message `"Are you sure you want to remove everything at path 'cp://{storage_name}/{file1_name}'? [y/N]:"` is printed
2.	File isn't deleted
3.	Error message "Are you sure you want to remove everything at path 'cp://{storage_name}/{file1_name}'? [y/N]:" is printed
4.	File is deleted
5.	File is deleted