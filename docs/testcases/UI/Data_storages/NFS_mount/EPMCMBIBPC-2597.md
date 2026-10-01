# Navigate to sub-folder in NFS mount

Test verifies navigating into a subfolder and back via the double dot on an NFS mount.

**Prerequisites**:
- Perform [EPMCMBIBPC-2276](EPMCMBIBPC-2276.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the folder created at step 4 of the [EPMCMBIBPC-2276](EPMCMBIBPC-2276.md) case | The parent folder is displayed (before this step) |
| 2 | Click **+ Create v** button |  |
| 3 | Click on the **Folder** item |  |
| 4 | Specify a valid folder name in the appeared pop-up window |  |
| 5 | Click **OK** button | The sub-folder is displayed |
| 6 | Click on the created folder name | Empty folder content is displayed |
| 7 | Click on `..` | The sub-folder is displayed |
| 8 | Click on `..` | The parent folder is displayed |
