# LS operation paging

**Requirements:**  
Create versioning storage. Put several files and several non-empty folders into.
Login as admin or created storage Owner.

**Actions**:
1.	Call `pipe storage ls -p 5 cp://{storage_name}`
2.	Call `pipe storage ls -p 5 -r cp://{storage_name}`
3.	Call `pipe storage ls -p 5 -l cp://{storage_name}`
4.	Call `pipe storage ls -p 5 -v cp://{storage_name}`
5.	Call `pipe storage ls -p 5 -l -v cp://{storage_name}`
6.	Call `pipe storage ls -p 5 -l -r cp://{storage_name}`
7.	Call `pipe storage ls -p 5 -l -v -r cp://{storage_name}`

***
**Expected result:**

1.	Record about 5 objects of storage located on top level is returned
2.	Record about 5 objects of storage located on top level and  in subfolders is returned
3.	Record about 5 objects of storage located on top level with detailed information is returned
4.	Record about 5 objects of storage located on top level with versions is returned
5.	Record about 5 objects of storage located on top level with detailed information and versions is returned
6.	Record about 5 objects of storage located on top level and  in subfolders with detailed information is returned
7.	Record about 5 objects of storage located on top level and  in subfolders with detailed information and versions is returned