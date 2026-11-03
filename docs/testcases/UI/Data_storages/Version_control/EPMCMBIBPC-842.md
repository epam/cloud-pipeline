# Delete empty folder validation

Test verifies that a non-admin user can permanently delete a folder they created, and that the admin sees it removed when showing versions.

**Prerequisites**:
- Perform [EPMCMBIBPC-813](EPMCMBIBPC-813.md)
- Logout
- Login as a user without an admin role

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Open the storage from the [EPMCMBIBPC-813](EPMCMBIBPC-813.md) case |  |
| 3 | Hover over **+ Create v** button |  |
| 4 | Select **Folder** item |  |
| 5 | Specify a valid folder name in the appeared pop-up window |  |
| 6 | Click **OK** button |  |
| 7 | Click on the delete icon opposite the created folder name |  |
| 8 | Click **OK** button in the appeared pop-up window |  |
| 9 | Logout |  |
| 10 | Login as Admin |  |
| 11 | Open library and the storage from step 2 |  |
| 12 | Set the **Show file versions** checkbox if it's unset | The removed folder doesn't display |
