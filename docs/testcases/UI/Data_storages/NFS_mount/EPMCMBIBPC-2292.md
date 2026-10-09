# [MANUAL] Validation of remove objects from NFS mount

Test verifies canceling and confirming the deletion of a single file, a single folder, and multiple selected objects on an NFS mount.

**Prerequisites**:
- Perform [EPMCMBIBPC-2290](EPMCMBIBPC-2290.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the delete icon opposite a file |  |
| 2 | Click **Cancel** button in the appeared dialog | The file is not deleted |
| 3 | Click the delete icon opposite a file |  |
| 4 | Click **OK** button in the appeared dialog | The file opposite which the user clicked the delete icon is deleted |
| 5 | Click the delete icon opposite a folder |  |
| 6 | Click **Cancel** button in the appeared dialog | The folder is not deleted |
| 7 | Click the delete icon opposite a folder |  |
| 8 | Click **OK** button in the appeared dialog | The folder opposite which the user clicked the delete icon is deleted |
| 9 | Click **Select page** button | The checkboxes opposite all objects switch to the checked state |
| 10 | Click **Remove all selected** button |  |
| 11 | Click **Cancel** button in the appeared dialog | The objects are not deleted, the checkboxes stay checked |
| 12 | Click **Remove all selected** button |  |
| 13 | Click **OK** button in the appeared dialog | All objects displayed on the page are deleted, the objects from the next page are displayed, the checkboxes switch to the unchecked state |
