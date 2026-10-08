# [MANUAL] Check detached configuration permissions for read+execute user

Test verifies the detached configuration UI for a user granted read+execute permissions.

**Prerequisites**:
- The user must not belong to a group that has access permissions to folders and configurations

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1641](EPMCMBIBPC-1641.md) case, checking the **execute** checkbox in the **allow** column (the **read** checkbox will be checked automatically) | <li>On the configuration tab, the **Run** button is displayed <li>On the configuration tab, the buttons **Save**, the gear icon (settings) in the top-right corner, and **+ ADD** are not displayed <li>The **Launch cluster** and **Start idle** checkboxes, the **Add parameter** button and the input fields are inactive |
