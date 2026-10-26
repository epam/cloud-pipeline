# Create folder that have name of existing folder in bucket

Test verifies that creating a folder with a name that already exists in the bucket is rejected.

**Prerequisites**:
- The storage created in [EPMCMBIBPC-448](EPMCMBIBPC-448.md) is selected

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Hover the mouse pointer over the **+ Create v** button, select **Folder** item |  |
| 2 | Input a valid value into the **Name** field in the pop-up window |  |
| 3 | Click **OK** button |  |
| 4 | Hover the mouse pointer over the **+ Create v** button, select **Folder** item |  |
| 5 | Input the value equal to the value from step 2 in the pop-up window |  |
| 6 | Click **OK** button | An error message like `Folder already exists` is displayed |

**After**:
- Delete the created folder
