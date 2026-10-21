# [MANUAL] Entities manager role validation

Test verifies uploading a metadata file into a directory as a user granted the ROLE_ENTITIES_MANAGER role.

**Prerequisites**:
- The user, and/or the group they belong to, has all rights (read, write, execute) in exactly one directory in the library
- The user has valid metadata files on their workstation

**Preparations**:
1. Login as admin
2. Press the settings button at the navigation panel
3. Open the **User Management** tab
4. Find the user from the prerequisites
5. Press the edit button opposite the user name
6. Add ROLE_ENTITIES_MANAGER
7. Press **OK**
8. Log out

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Open the directory from the prerequisites | The **Upload metadata** button is visible in the upper-right corner |
| 3 | Press the **Upload metadata** button in the upper-right corner | An OS browse window appears |
| 4 | In the OS window that opens, navigate to a valid metadata file |  |
| 5 | Select the file |  |
| 6 | Confirm the file upload | A new metadata entity appears in the current folder in the hierarchy tree |
