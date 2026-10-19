# [MANUAL] Folder manager role validation

Test verifies creating a subfolder in a directory as a user granted the ROLE_FOLDER_MANAGER role.

**Prerequisites**:
- The user, and/or the group they belong to, has all rights (read, write, execute) in exactly one directory in the pipeline library
- The user owns no entity in the pipeline library

**Preparations**:
1. Login as admin
2. Press the settings button at the navigation panel
3. Open the **User Management** tab
4. Find the user from the prerequisites
5. Press the edit button opposite the user name
6. Add ROLE_FOLDER_MANAGER
7. Press **OK**
8. Log out

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Open the directory from the prerequisites | The **+ Create** button is visible in the upper-right corner |
| 3 | Press the **+ Create** button in the upper-right corner | A drop-down list with the **Folder** sign appears |
| 4 | Click the **Folder** sign |  |
| 5 | Enter a name in the pop-up that appears |  |
| 6 | Press **OK** | A new subfolder with the name from action step 5 appears in the hierarchy tree |
