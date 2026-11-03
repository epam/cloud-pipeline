# [MANUAL] Check tool edit prohibition for read-only user

Test verifies that a read-only user cannot edit a tool's versions or settings.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Complete the [EPMCMBIBPC-1620](EPMCMBIBPC-1620.md) case |  |
| 2 | Open the tool | No **EDIT**, **Run**, or settings buttons are present |
| 3 | Click the **VERSIONS** tab | No **Run**, settings, or delete buttons are present |
| 4 | Click the **SETTINGS** tab | <li>No **Run** or settings buttons are present <li>The **Save**, **DELETE**, **+ New Endpoint**, **+ New Label** buttons are not clickable <li>The **Disk (Gb)** and **Instance type** fields are not clickable <li>Commands in the **Default command** field can be selected but not changed |
