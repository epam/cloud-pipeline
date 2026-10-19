# [MANUAL] Validation of create active notification paired with empty active notification

Test verifies that a notification with an empty Body and one with a filled Body are both displayed correctly at the same time.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the **Settings** button (navigation-button-settings) |  |
| 2 | Click the **System events** tab |  |
| 3 | Click **+ ADD** |  |
| 4 | Enter a value into the **Title** field |  |
| 5 | Leave the **Body** field empty |  |
| 6 | Click the **Severity** combobox |  |
| 7 | Select **Warning** |  |
| 8 | Enable the **Active** checkbox |  |
| 9 | Click **CREATE** |  |
| 10 | Repeat steps 3-9, filling in the **Body** field |  |
| 11 | Click **OK** |  |
| 12 | Refresh the page | Both notifications are displayed: <li>Severity - Warning <li>the title is equal to the **Title** value <li>the text is equal to the **Body** value |
