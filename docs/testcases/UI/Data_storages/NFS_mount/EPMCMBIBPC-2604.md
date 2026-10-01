# Create files and folders with spaces in name

Test verifies creating a folder and a file whose names contain spaces, at the root and in a subfolder.

**Prerequisites**:
- Perform [EPMCMBIBPC-2603](EPMCMBIBPC-2603.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the NFS mount created in the [EPMCMBIBPC-2603](EPMCMBIBPC-2603.md) case |  |
| 2 | Click **+ Create v** button |  |
| 3 | Click on the **Folder** item |  |
| 4 | Specify a folder name with spaces in the appeared pop-up window |  |
| 5 | Click **OK** button |  |
| 6 | Click **Upload** button |  |
| 7 | Select a file with spaces in its name and confirm the upload | The folder and file with spaces in their names are displayed correctly |
| 8 | Open the folder created at step 5 |  |
| 9 | Repeat steps 2-7 | The folder and file with spaces in their names are displayed correctly in the sub-folder |
