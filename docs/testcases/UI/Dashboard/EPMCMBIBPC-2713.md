# [MANUAL] ACTIVITIES widget

Test verifies the ACTIVITIES widget's records for creating an issue and commenting on it.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Hover over **+ Create v**, click **Folder** |  |
| 3 | Specify a valid folder name, click **OK** |  |
| 4 | Click the issues button in the row next to the just-created folder |  |
| 5 | On the **Issues** panel, click **New issue** |  |
| 6 | Enter a valid title and description for the issue, click **Create** |  |
| 7 | On the **Issues** panel, click on the just-created issue |  |
| 8 | Enter comment text, click **Send** |  |
| 9 | Open the **Home** page |  |
| 10 | Click **Configure** in the upper-right corner |  |
| 11 | In the pop-up that appears, set the checkbox near **Activities** and unset the others |  |
| 12 | Click **OK** |  |
| 13 | Refresh the page | The **ACTIVITIES** widget shows at least 2 records: <li>for the comment created at step 8, containing: a header with the text "You commented issue {issue title} for folder {folder name} {some time} ago", where {issue title} is the issue title specified at step 6 and {folder name} is the folder name specified at step 3; and text equal to the comment message entered at step 8 <li>for the issue created at step 6, containing: a header with the text "You created new issue {issue title} for folder {folder name} {some time} ago", where {issue title} is the issue title specified at step 6 and {folder name} is the folder name specified at step 3; and text equal to the issue description entered at step 6 |
| 14 | Click the folder link in the record for the issue created at step 6 | The folder created at step 3 opens |
