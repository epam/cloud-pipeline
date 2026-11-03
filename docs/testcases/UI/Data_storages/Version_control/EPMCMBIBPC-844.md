# Mark for deletion not empty folder

Test verifies that marking a non-empty folder for deletion hides it, and that showing versions reveals its file colored as deleted.

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
| 9 | Click on the delete icon opposite the folder name created at steps 1-4 |  |
| 10 | Click **OK** button in the appeared pop-up window | The folder doesn't display |
| 11 | Set the **Show files versions** checkbox |  |
| 12 | Open the appeared folder | The file uploaded at step 7 is displayed and colored red |
