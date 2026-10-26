# [MANUAL] Create issue

Test verifies creating an issue for a folder.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Hover over **+ Create v**, click **Folder** in the list that appears |  |
| 3 | In the pop-up that appears, specify a valid folder name, click **OK** |  |
| 4 | Open the just-created folder |  |
| 5 | Click the button in front of the gear icon in the upper-right corner |  |
| 6 | Click **Issues** in the list that appears | The **Issues** panel appears, containing: <li>the folder name specified at step 3 <li>the **New issue** button <li>the "No issues found" label |
| 7 | Click **New issue** | In the **Issues** panel: <li>an empty **Title** field <li>the **Write**, **Preview** tabs (the **Write** tab, with an empty description, is active) <li>the **Cancel**, **Create** buttons |
| 8 | Specify a valid issue title |  |
| 9 | Enter some text into the description field |  |
| 10 | Click **Create** | In the **Issues** panel, a new record for the issue appears, containing: <li>the issue title equal to the one specified at step 8 <li>the text "Opened {some time} ago by {USER_NAME}", where {USER_NAME} is the name of the user who created the issue |
