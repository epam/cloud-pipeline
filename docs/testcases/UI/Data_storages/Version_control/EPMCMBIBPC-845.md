# Delete not empty folder

Test verifies permanently deleting a non-empty folder from the versioned listing.

**Prerequisites**:
- Perform [EPMCMBIBPC-813](EPMCMBIBPC-813.md)
- Uncheck the **Show files versions** checkbox

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Hover over **+ Create v** button |  |
| 2 | Select **Folder** item |  |
| 3 | Specify a valid folder name in the appeared pop-up window |  |
| 4 | Click **OK** button |  |
| 5 | Open the created folder |  |
| 6 | Click **Upload** button |  |
| 7 | Select a file and confirm the upload in the appeared pop-up window |  |
| 8 | Click `..` |  |
| 9 | Set the **Show files versions** checkbox |  |
| 10 | Click on the delete icon opposite the folder name created at steps 1-4 |  |
| 11 | Click **Delete from bucket** button in the appeared pop-up window | The folder and all its files are permanently deleted |
