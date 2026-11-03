# [MANUAL] Folder navigation in NFS mount

Test verifies navigating into a nested folder structure and back up, on an NFS mount.

**Prerequisites**:
- Perform [EPMCMBIBPC-2276](EPMCMBIBPC-2276.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the folder created at step 4 of the [EPMCMBIBPC-2276](EPMCMBIBPC-2276.md) case |  |
| 2 | Click **+ Create v** button |  |
| 3 | Click on the **Folder** item |  |
| 4 | Specify a valid folder name in the appeared pop-up window |  |
| 5 | Click **OK** button |  |
| 6 | Click **+ Create v** button |  |
| 7 | Click on the **File** item |  |
| 8 | Specify a valid file name in the appeared pop-up window, input text into the **Content** field |  |
| 9 | Click **OK** button |  |
| 10 | Click on the folder name created at step 5 |  |
| 11 | Click on `..` | A transition occurs to the parent level |
| 12 | Click on `..` |  |
| 13 | Click on the folder name from step 1 | The folder content is displayed |
