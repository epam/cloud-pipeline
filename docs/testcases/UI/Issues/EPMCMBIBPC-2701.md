# [MANUAL] Create issue for folder

Test verifies creating an issue for a subfolder, and that returning from it shows the parent folder's own issue.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |  |
| 2 | Open the **Library** page, navigate to the folder created at step 3 of the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |  |
| 3 | Hover over **+ Create v**, click **Folder** in the list that appears |  |
| 4 | In the pop-up that appears, specify a valid folder name, click **OK** |  |
| 5 | Click the issues button (in front of the **Delete** button) in the row with the just-created folder | The **Issues** panel appears, containing: <li>the folder name equal to the one specified at step 4 <li>the **New issue** button <li>the "No issues found" label |
| 6 | Click **New issue** |  |
| 7 | Specify valid values for the issue title and description, click **Create** | <li>In the **Issues** panel, a new record for the issue is displayed, containing: the issue title equal to the one specified at step 7; the text "Opened {some time} ago by {USER_NAME}", where {USER_NAME} is the name of the user who created the issue <li>In the row with the folder created at step 4, the number "1" appears on the issues button (in front of the **Delete** button) |
| 8 | Click **return** in the **Issues** panel | The following appear in the **Issues** panel: <li>the folder name equal to the one specified at step 3 of the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case <li>the record for the issue created at the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case, containing the title specified at step 8 of the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case, and the text "Opened {some time} ago by {USER_NAME}", where {USER_NAME} is the name of the user who created the issue at the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |
