# [MANUAL] Issue formatting

Test verifies user-mention and text-formatting markup in an issue's description.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |  |
| 2 | Click on the issue created at step 1 |  |
| 3 | Click **Edit** in the issue description section |  |
| 4 | Remove the description text |  |
| 5 | Enter the "@" symbol | The **Search users** field appears |
| 6 | Enter an existing user name after the "@" symbol |  |
| 7 | In the **Search users** field that appears, click on the user name from step 6 |  |
| 8 | Click the **Preview** tab | In the preview description section, the user name specified at step 6 is displayed in bold font |
| 9 | Click the **Write** tab |  |
| 10 | Enter the following text into the description field after the user name: `_cursive_ **bold**` |  |
| 11 | Click the "V" button in the upper-right corner of the issue description section | In the description section, text is displayed that contains: <li>the user name specified at step 6, in bold font <li>the word "cursive", in cursive (italic) font <li>the word "bold", in bold font |
