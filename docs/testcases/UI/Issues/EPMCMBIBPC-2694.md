# [MANUAL] [NEGATIVE] Edit issue

Test verifies that clearing an issue's title or description in place is rejected.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |  |
| 2 | Click on the issue created at step 1 |  |
| 3 | Click on the issue title |  |
| 4 | Remove the issue title |  |
| 5 | Click on an empty area | The error notification "Field should not be empty" appears |
| 6 | Click **Return** | In the **Issues** panel, the record for the issue with the title specified at step 8 of the EPMCMBIBPC-2691 case is displayed |
| 7 | Repeat step 2 |  |
| 8 | Click **Edit** in the issue description section |  |
| 9 | Remove the description of the issue |  |
| 10 | Click the "V" button in the upper-right corner of the issue description section | <li>The error notification "Issue content text is invalid or does not specified" appears <li>A few seconds later, the previous issue description (specified at step 9 of the EPMCMBIBPC-2691 case) appears |
