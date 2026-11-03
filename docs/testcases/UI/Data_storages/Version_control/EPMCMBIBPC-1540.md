# Delete one of sibling files

Test verifies that permanently deleting a file whose name is a prefix of another file's name doesn't affect the sibling file.

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
| 7 | Open the created storage, make sure the **Show files versions** checkbox is unchecked |  |
| 8 | Create `file_name` and `file_name.csv` files |  |
| 9 | Click on the delete icon for the `file_name` file |  |
| 10 | Click **OK** button in the appeared pop-up window |  |
| 11 | Set the **Show files versions** checkbox |  |
| 12 | Click on the delete icon for the `file_name` file that was marked as deleted (colored red) |  |
| 13 | Click **OK** button in the appeared pop-up window | The `file_name.csv` file still exists |
