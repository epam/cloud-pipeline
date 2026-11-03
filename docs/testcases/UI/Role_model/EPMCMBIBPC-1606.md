# [MANUAL] Pipeline manager role validation

Test verifies creating a pipeline in a directory as a user granted the ROLE_PIPELINE_MANAGER role.

**Prerequisites**:
- The user, and/or the group they belong to, has all rights (read, write, execute) in exactly one directory in the pipeline library

**Preparations**:
1. Login as admin
2. Press the settings button at the navigation panel
3. Open the **User management** tab
4. Find the user from the prerequisites
5. Press the edit button opposite the user name
6. Add ROLE_PIPELINE_MANAGER
7. Press **OK**
8. Log out

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the user from the prerequisites |  |
| 2 | Open the directory from the prerequisites | The **+ Create** button is visible in the upper-right corner |
| 3 | Press the **+ Create** button in the upper-right corner | A drop-down list with only the **Pipeline** sign appears |
| 4 | Hover over the **Pipeline** sign | A drop-down list with the different pipeline types appears |
| 5 | Click **SHELL** |  |
| 6 | Enter a name in the pop-up that appears |  |
| 7 | Press **OK** | A new shell pipeline with the name from action step 6 appears in the current folder in the hierarchy tree |
