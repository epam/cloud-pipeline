# [Negative] Create existing folder/file in NFS mount, validation

Test verifies that creating a folder with a name that already exists on the NFS mount is rejected.

**Prerequisites**:
- Perform [EPMCMBIBPC-2276](EPMCMBIBPC-2276.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **+ Create v** button |  |
| 2 | Click on the **Folder** item |  |
| 3 | Specify the name of the existing folder in the appeared pop-up window |  |
| 4 | Click **OK** button | An error message is displayed: `Could not create a folder in nfs: /{folder_name}` |
