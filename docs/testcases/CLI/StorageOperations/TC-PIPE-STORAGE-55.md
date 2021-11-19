# [Negative] Try to create folder that have empty name

**Actions**:
1.	Call: `pipe storage mkdir cp://{storage_name}/`
2.	Call: `pipe storage ls cp://{storage_name}/`
3.	Call: `pipe storage mkdir cp://{storage_name}//`
4.	Call: `pipe storage ls cp://{storage_name}/`

***
**Expected result:**

1.	Error message is returned
2.	The number of directories isn't changed
3.	Error message is returned
4.	The number of directories isn't changed