# Create folder/file in NFS mount, validation

Test verifies creating a folder and a file on an NFS mount.

**Prerequisites**:
- Perform [EPMCMBIBPC-2274](EPMCMBIBPC-2274.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **+ Create v** button |  |
| 2 | Click on the **Folder** item |  |
| 3 | Specify a valid folder name in the appeared pop-up window |  |
| 4 | Click **OK** button |  |
| 5 | Click **+ Create v** button |  |
| 6 | Click on the **File** item |  |
| 7 | Specify a valid file name in the appeared pop-up window, input text into the **Content** field |  |
| 8 | Click **OK** button |  |

**Expected results**:

- After step 6: a folder with the name from step 3 appears in the storage content
- After step 8: a file with the name from step 7 appears in the storage content. Its size is more than zero
