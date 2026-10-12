# [MANUAL] Check detached configuration permissions for read+write user

Test verifies the detached configuration UI for a user granted read+write permissions.

**Prerequisites**:
- The user must not belong to a group that has access permissions to folders and configurations

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1641](EPMCMBIBPC-1641.md) case, checking the **write** checkbox in the **allow** column (the **read** checkbox will be checked automatically) | <li>On the configuration tab, the **Run** button is not displayed <li>On the configuration tab, the buttons **Save**, the gear icon (settings) in the top-right corner, and **+ ADD** are displayed <li>The **Launch cluster** and **Start idle** checkboxes, the **Add parameter** button and the input fields are active |
| 2 | Click the **Name** input field |  |
| 3 | Change the configuration name | The configuration name is changed |
