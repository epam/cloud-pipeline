# Delete file by non admin user

Test verifies that a non-admin user with READ and WRITE permissions can delete a file, and that the admin later sees both the delete marker and the restored-size history when showing versions.

**Prerequisites**:
- Perform [EPMCMBIBPC-813](EPMCMBIBPC-813.md)
- Logout
- Login as a user without an admin role, with READ and WRITE permissions on the storage from the [EPMCMBIBPC-813](EPMCMBIBPC-813.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Open the storage from the [EPMCMBIBPC-813](EPMCMBIBPC-813.md) case |  |
| 3 | Click on the delete icon opposite the file name |  |
| 4 | Click **OK** button in the appeared pop-up window | The file doesn't display |
| 5 | Logout |  |
| 6 | Login as Admin |  |
| 7 | Open library |  |
| 8 | Open the storage from the [EPMCMBIBPC-813](EPMCMBIBPC-813.md) case |  |
| 9 | Set **Show files versions** | The file is displayed and has size 0 bytes |
| 10 | Click the `+` button in front of the file name that was deleted at steps 3-4 | <li> the row with the file name, deleting time and label `latest` is displayed (file size is 0 bytes) <li> below it, the row with the file name and the time of the last change is displayed (file size is not 0 bytes); in that same row, in front of the file name, the **Download** and **Restore** buttons are displayed |
