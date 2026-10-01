# [MANUAL] Validation of price types restrictions (for user group)

Test verifies that a price-types mask set on ROLE_USER restricts the Price type dropdown the same way as a per-user mask.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **User management** tab in system settings |  |
| 3 | Click the **Roles** tab, click the edit icon opposite **ROLE_USER** |  |
| 4 | In the pop-up that appears, click the **Allowed price types** combobox, select a value in the drop-down list (e.g. `On demand`) |  |
| 5 | Click **OK** |  |
| 6 | Log out |  |
| 7 | Login as the non-admin user from the prerequisites of the EPMCMBIBPC-2637 case |  |
| 8 | Repeat steps 8-19 of the [EPMCMBIBPC-2648](EPMCMBIBPC-2648.md) case | The results are the same as in the EPMCMBIBPC-2648 case |

**After**:
- Clear the **Allowed price types** field changed at step 4
