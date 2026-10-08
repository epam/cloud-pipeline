# Rerun pipeline without permissions for execute

Test verifies that rerunning a completed run is denied once execute permission is revoked, while read remains granted.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-553](EPMCMBIBPC-553.md) case |  |
| 2 | Log out |  |
| 3 | Login as admin |  |
| 4 | Open the **Library** page |  |
| 5 | Click on the pipeline selected at step 7 of the [EPMCMBIBPC-553](EPMCMBIBPC-553.md) case |  |
| 6 | Click the gear icon in the upper-right corner |  |
| 7 | Click the **Permissions** tab |  |
| 8 | Click on the user name from step 5 of the [EPMCMBIBPC-553](EPMCMBIBPC-553.md) case |  |
| 9 | Set the checkbox in the **Deny** column opposite the **EXECUTE** permission (the **READ** permission checkbox remains checked) |  |
| 10 | Close the pop-up |  |
| 11 | Log out |  |
| 12 | Login as the user selected at step 8 |  |
| 13 | Open the **Library** page |  |
| 14 | Click on the pipeline selected at step 5 |  |
| 15 | Click on the pipeline version |  |
| 16 | Click the **HISTORY** tab |  |
| 17 | Click the **RERUN** hyperlink opposite the run completed at step 1 | An error message like "You have no permissions" appears |
