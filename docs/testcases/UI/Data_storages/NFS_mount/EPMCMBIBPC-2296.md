# [MANUAL] Validation of spaces in objects names for NFS mount

Test verifies creating a folder and a file with spaces in their names on an NFS mount, including in a subfolder, and that repeating the creation doesn't duplicate them.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to the created NFS storage |  |
| 2 | Click **Create** button |  |
| 3 | Click the **Folder** item |  |
| 4 | Enter a folder name with spaces and click **OK** | The folder with spaces in its name is displayed |
| 5 | Click **Create** button |  |
| 6 | Click the **File** item |  |
| 7 | Enter a file name with spaces and click **OK** | The file with spaces in its name is displayed |
| 8 | Go to the folder created at step 4 |  |
| 9 | Repeat steps 2-7 | The file and folder with spaces in their names are displayed |
| 10 | Click on the double dot | The file/folder structure doesn't change (no duplicate objects appear) |
