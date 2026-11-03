# [MANUAL] Check detached configuration permissions for read-only user

Test verifies the detached configuration UI for a user granted read-only permissions.

**Prerequisites**:
- The user must not belong to a group that has access permissions to folders and configurations

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-537](EPMCMBIBPC-537.md) case for a configuration |  |
| 2 | Click the edit icon in the top-right corner |  |
| 3 | Click the **Permissions** tab |  |
| 4 | Click on the user |  |
| 5 | Click the checkbox in the **allow** column opposite the **read** privilege |  |
| 6 | Log out |  |
| 7 | Login as the user from the prerequisites |  |
| 8 | Open the configuration |  |
| 9 | Expand the **Advanced** tab | <li>In the tree on the left panel, only the path to the configuration for which the permission was granted is displayed <li>On the configuration tab, the buttons **Run**, **Save**, the gear icon (settings) in the top-right corner, and **+ ADD** are not displayed <li>The **Launch cluster** and **Start idle** checkboxes, the **Add parameter** button and the input fields are inactive |
