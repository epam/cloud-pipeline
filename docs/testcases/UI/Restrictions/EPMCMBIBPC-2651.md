# [MANUAL] Validation of price types restrictions (system settings)

Test verifies that a global price-types mask restricts the Price type dropdown the same way as a per-user mask.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Preferences** tab in system settings |  |
| 3 | Click the **Cluster** menu item on the left panel |  |
| 4 | Specify allowed price types into **cluster.allowed.price.types** (e.g. `on_demand`) |  |
| 5 | Click **Save** |  |
| 6 | Log out |  |
| 7 | Login as the non-admin user from the EPMCMBIBPC-2648 case |  |
| 8 | Repeat steps 8-19 of the [EPMCMBIBPC-2648](EPMCMBIBPC-2648.md) case | The results are the same as in the EPMCMBIBPC-2648 case |

**After**:
- Restore the previous value of the **cluster.allowed.price.types** field, as it was before step 4
