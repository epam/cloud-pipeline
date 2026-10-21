# Restore file validation

Test verifies restoring a deleted file, and that a non-admin user then sees it back.

**Prerequisites**:
- Perform [EPMCMBIBPC-815](EPMCMBIBPC-815.md)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on the **Restore** button opposite the deleted file name | Before this step, the list of all file versions is displayed, with a file labeled `latest` (0 bytes) in the first row; after this step, the file labeled `latest` is no longer 0 bytes |
| 2 | Logout |  |
| 3 | Login as a user without an admin role | The file that was deleted in the [EPMCMBIBPC-815](EPMCMBIBPC-815.md) case is displayed |
