# Validation of price types restrictions (over instance management)

Test verifies that a per-tool price-types mask applied via Instance management restricts the Price type dropdown, and hides the Instance management item from non-admin users.

**Prerequisites**:
- An existing non-admin user
- An existing scanned tool
- The user, or their group, has permissions to launch that tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **Tools** page |  |
| 3 | Select a registry and group, click on the tool from the prerequisites |  |
| 4 | Hover over the icon to the left of the gear icon in the upper-right corner |  |
| 5 | Click **Instance management** in the list that appears |  |
| 6 | In the panel that appears, click the **Allowed price types** combobox, select a value in the drop-down list (e.g. `On demand`) |  |
| 7 | Click **APPLY** |  |
| 8 | Hover over the **v** button near **Run**, click **Custom settings** in the list that appears |  |
| 9 | Expand the **Advanced** section if it's minimized |  |
| 10 | Click the **Price type** combobox | Only the value selected at step 6 appears in the drop-down list |
| 11 | Log out |  |
| 12 | Login as the user from the prerequisites |  |
| 13 | Repeat steps 2-4 | The **Instance management** item is not displayed |
| 14 | Repeat steps 8-10 | Only the value selected at step 6 appears in the drop-down list |

**After**:
- Clear the **Allowed price types** field that was changed at step 6
