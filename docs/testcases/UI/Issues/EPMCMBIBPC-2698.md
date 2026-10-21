# [MANUAL] Edit comment to issue

Test verifies editing an issue comment, including user-mention and text-formatting markup.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2697](EPMCMBIBPC-2697.md) case |  |
| 2 | Click on the issue created at the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |  |
| 3 | Click **Edit** near the comment created at step 1 |  |
| 4 | Click the "X" button in the upper-right corner of the comment section | The comment text is equal to the one created at step 1 |
| 5 | Repeat step 3 |  |
| 6 | Remove the comment text |  |
| 7 | Click the "V" button in the upper-right corner of the comment section | The error notification "Comment content text is invalid or does not specified" appears |
| 8 | Enter the "@" symbol into the comment text field | The **Search users** field appears |
| 9 | Enter an existing user name after the "@" symbol |  |
| 10 | In the **Search users** field that appears, click on the user name from step 9 |  |
| 11 | Click the **Preview** tab | In the preview comment text field, the user name specified at step 9 is displayed in bold font |
| 12 | Click the **Write** tab |  |
| 13 | Enter the following text into the comment text field after the user name: `_cursive_ **bold**` |  |
| 14 | Click the "V" button in the upper-right corner of the comment section | In the comment section, text is displayed that contains: <li>the user name specified at step 9, in bold font <li>the word "cursive", in cursive (italic) font <li>the word "bold", in bold font |
