# Commit docker validation in library group

Test verifies committing a run's container to the library group, including the overwrite-confirmation flow.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform steps 1-9 of the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 2 | Wait until the **SSH** hyperlink appears in the right upper corner |  |
| 3 | Click the **SSH** hyperlink |  |
| 4 | In the opened tab, in the terminal run: `cd /` |  |
| 5 | Run: `echo 'This is a test file {random int number}' > test_file.txt` |  |
| 6 | Close the SSH tab and go back to the tab opened at step 1 |  |
| 7 | Click the **COMMIT** hyperlink |  |
| 8 | Click on `{registry_name}` |  |
| 9 | Select **Default registry** |  |
| 10 | Select the tools group from step 3 of the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 11 | Input a name for the docker image equal to the tool name selected at step 4 of the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 12 | Click **COMMIT** button | A pop-up appears containing: a warning message like `<docker image> already exists. Overwrite?`; **CANCEL**, **OK** buttons |
| 13 | Click **OK** button | <li> the pop-up is closed <li> in the right upper corner, the label **COMMITING** appears <li> the commit finishes successfully |

**After**:
- Stop the run
