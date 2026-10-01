# Validation of "Show files versions" behavior

Test verifies that checking **Show files versions** on a versioned storage reveals a **+** button in front of every file.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v** button |  |
| 3 | Select **Storages** -> **Create new object storage** |  |
| 4 | Specify storage path |  |
| 5 | Set the **Enable versioning** checkbox if it's unset |  |
| 6 | Click **Create** button |  |
| 7 | Open the created storage |  |
| 8 | Hover over **+ Create v** button |  |
| 9 | Select **Folder** item |  |
| 10 | Specify a valid folder name in the appeared pop-up window |  |
| 11 | Click **OK** button |  |
| 12 | Click **Upload** button |  |
| 13 | Select a file and confirm the upload in the appeared pop-up window |  |
| 14 | Set the **Show files versions** checkbox | <li> the **Show files versions** checkbox is checked <li> `+` buttons are displayed in front of all files |
