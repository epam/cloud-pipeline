# Validation of "Refresh" button

Test verifies that the **Refresh** button reloads the storage content from the backend.

**Prerequisites**:
- The storage created in [EPMCMBIBPC-448](EPMCMBIBPC-448.md) is selected

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Hover the mouse pointer over the **+ Create v** button, select **Folder** item |  |
| 2 | Input a valid value into the **Name** field in the pop-up window |  |
| 3 | Click **OK** button | There is no folder with the name entered at step 2 in the storage |
| 4 | Click **Refresh** button in the right upper corner | There is a folder with the name entered at step 2 in the storage |

**After**:
- Delete the created folder
