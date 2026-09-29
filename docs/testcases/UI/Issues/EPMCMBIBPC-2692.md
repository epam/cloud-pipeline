# [MANUAL] [NEGATIVE] Create issue

Test verifies that creating an issue without a title or description is rejected.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-2691](EPMCMBIBPC-2691.md) case |  |
| 2 | Click **New issue** |  |
| 3 | Click **Create** | <li>The error message "Issue title is required" is displayed <li>The error notification "Issue description is required" appears |
| 4 | Specify a valid issue title |  |
| 5 | Repeat step 3 | The error notification "Issue description is required" appears |
| 6 | Click **Cancel** | In the **Issues** panel, only the record for the issue created in the EPMCMBIBPC-2691 case is displayed |
