# [MANUAL] Validation of price types restrictions (hierarchy)

*Note: Restrictions: some of this functionality is supported by a specific platform only (this test case should be disabled for Azure)*

Test verifies that a per-user price-types mask takes precedence over a per-tool mask applied via Instance management.

**Prerequisites**:
- An existing non-admin user
- An existing scanned tool
- The user, or their group, has permissions to launch that tool
- The [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case is performed

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Preferences** tab in system settings |  |
| 3 | Click the **Cluster** menu item on the left panel |  |
| 4 | Specify allowed price types into **cluster.allowed.price.types** (e.g. `spot,on_demand`) |  |
| 5 | Click **Save** |  |
| 6 | Open the **Tools** page |  |
| 7 | Select a registry and group, click on the tool from the prerequisites |  |
| 8 | Hover over the icon to the left of the gear icon in the upper-right corner |  |
| 9 | Click **Instance management** in the list that appears |  |
| 10 | In the panel that appears, click the **Allowed price types** combobox, select a value in the drop-down list (e.g. `Spot`) |  |
| 11 | Click **APPLY** |  |
| 12 | Log out |  |
| 13 | Login as the user from the prerequisites |  |
| 14 | Open the **Library** page, navigate to the folder created at step 9 of the EPMCMBIBPC-2637 case |  |
| 15 | Open the pipeline created at step 17 of the EPMCMBIBPC-2637 case, click the pipeline version |  |
| 16 | Click the **CONFIGURATION** tab, expand the **Advanced** collapsed header |  |
| 17 | Click the **Price type** combobox | The values specified at step 4 appear in the drop-down list |
| 18 | Repeat step 14 |  |
| 19 | Open the configuration created at step 24 of the EPMCMBIBPC-2637 case, expand the **Advanced** section |  |
| 20 | Click the **Price type** combobox | The values specified at step 4 appear in the drop-down list |
| 21 | Repeat steps 6-7 |  |
| 22 | Hover over the **v** button near **Run**, click **Custom settings** in the list that appears |  |
| 23 | Expand the **Advanced** section if it's minimized |  |
| 24 | Click the **Price type** combobox | Only the value selected at step 10 appears in the drop-down list |
| 25 | Log out |  |
| 26 | Login as admin |  |
| 27 | Open the **User management** tab in system settings |  |
| 28 | Click the **Users** tab |  |
| 29 | Click the edit icon opposite the user name from the prerequisites |  |
| 30 | In the pop-up that appears, click the **Allowed price types** combobox, select a value in the drop-down list (e.g. `On demand`) |  |
| 31 | Click **OK** |  |
| 32 | Repeat steps 12-13 |  |
| 33 | Repeat steps 21-24 | Only the value selected at step 30 appears in the drop-down list |

**After**:
- Restore the previous value of the **cluster.allowed.price.types** field, as it was before step 4, and clear the **Allowed price types** fields changed at steps 10, 30
