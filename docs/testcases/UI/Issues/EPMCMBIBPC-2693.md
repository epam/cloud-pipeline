# [MANUAL] Edit issue

Test verifies editing an issue's title and description, including cancelling an in-progress edit.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |  |
| 2 | Click on the issue created at step 1 | In the **Issues** panel: <li>the issue title equal to the one specified at step 8 of the EPMCMBIBPC-2691 case <li>the **Return**, **Delete** buttons <li>an issue description section containing: a header with the text "You commented {some time} ago"; the **Edit** button; a description equal to the one specified at step 9 of the EPMCMBIBPC-2691 case <li>the **Write**, **Preview** tabs <li>the **Send** button (disabled) |
| 3 | Click on the issue title |  |
| 4 | Specify a new valid title for the issue |  |
| 5 | Click on an empty area | In the **Issues** panel, the issue title equal to the one specified at step 4 is displayed |
| 6 | Click **Edit** in the issue description section |  |
| 7 | Specify a new description for the issue |  |
| 8 | Click the "X" button in the upper-right corner of the issue description section | A pop-up with the message "All changes will be lost. Continue?" appears |
| 9 | Click **OK** in the pop-up | The issue description is equal to the one specified at step 9 of the EPMCMBIBPC-2691 case |
| 10 | Repeat steps 6-8 |  |
| 11 | Click **Cancel** in the pop-up |  |
| 12 | Click the "V" button in the upper-right corner of the issue description section | The issue description is equal to the one specified at step 10 |
| 13 | Click **Return** (the button with an arrow) | In the **Issues** panel, the record for the issue with the title specified at step 4 is displayed |
