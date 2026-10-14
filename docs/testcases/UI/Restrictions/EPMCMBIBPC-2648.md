# [MANUAL] Validation of price types restrictions (over user management)

Test verifies that a per-user price-types mask restricts the Price type dropdown across a pipeline, a configuration and a tool run.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case
- An existing non-admin user
- An existing scanned tool
- The user, or their group, has permissions to launch that tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as admin |  |
| 2 | Open the **User management** tab in system settings |  |
| 3 | Click the edit icon opposite the user name from the prerequisites |  |
| 4 | In the pop-up that appears, click the **Allowed price types** combobox, select a value in the drop-down list (e.g. `On demand`) |  |
| 5 | Click **OK** |  |
| 6 | Log out |  |
| 7 | Login as the user from step 3 |  |
| 8 | Open the **Library** page, navigate to the folder created at step 9 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case |  |
| 9 | Open the pipeline created at step 17 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case, click the pipeline version |  |
| 10 | Click the **CONFIGURATION** tab, click the **Advanced** collapsed header |  |
| 11 | Click the **Price type** combobox | Only the value selected at step 4 appears in the drop-down list |
| 12 | Repeat step 8 |  |
| 13 | Open the configuration created at step 24 of the [EPMCMBIBPC-2637](EPMCMBIBPC-2637.md) case, expand the **Advanced** section |  |
| 14 | Click the **Price type** combobox | Only the value selected at step 4 appears in the drop-down list |
| 15 | Open the **Tools** page |  |
| 16 | Select a registry and group, click on the tool from the prerequisites |  |
| 17 | Hover over the **v** button near **Run**, click **Custom settings** in the list that appears |  |
| 18 | Expand the **Advanced** section if it's minimized |  |
| 19 | Click the **Price type** combobox | Only the value selected at step 4 appears in the drop-down list |

**After**:
- Clear the value set in the **Allowed price types** combobox at step 4
