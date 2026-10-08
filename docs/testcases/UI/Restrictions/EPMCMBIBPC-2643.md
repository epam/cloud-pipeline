# Validation of tools instance types restrictions (for user group)

Test verifies that a tool-instance-types mask set on ROLE_USER restricts the Node type dropdown the same way as a per-user mask.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2641](EPMCMBIBPC-2641.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Repeat steps 1-4 of the [EPMCMBIBPC-2641](EPMCMBIBPC-2641.md) case |  |
| 2 | In the pop-up that appears, clear the **Allowed tool instance types mask** field |  |
| 3 | Click **OK** |  |
| 4 | Click the **Roles** tab, click the edit icon opposite **ROLE_USER** |  |
| 5 | In the pop-up that appears, specify a mask value into **Allowed tool instance types mask** (e.g. `m5.*`) |  |
| 6 | Click **OK** |  |
| 7 | Log out |  |
| 8 | Login as the user from the prerequisites of the [EPMCMBIBPC-2641](EPMCMBIBPC-2641.md) case |  |
| 9 | Repeat steps 9-13 of the [EPMCMBIBPC-2641](EPMCMBIBPC-2641.md) case | A drop-down list appears with only the values of node types that comply with the mask entered at step 5 |

**After**:
- Clear the **Allowed tool instance types mask** field that was changed at step 5
