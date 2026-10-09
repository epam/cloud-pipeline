# Validation of commited docker

Test verifies that a file created via SSH before committing is present in a run launched from the committed image.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-694](EPMCMBIBPC-694.md) case |  |
| 2 | Open **Tools** page |  |
| 3 | Select **Default registry** |  |
| 4 | Select the tools group specified at step 10 of the [EPMCMBIBPC-694](EPMCMBIBPC-694.md) case |  |
| 5 | Click on the tool specified at step 11 of the [EPMCMBIBPC-694](EPMCMBIBPC-694.md) case |  |
| 6 | Click **SETTINGS** tab |  |
| 7 | Check that valid values are set in **Port**, **Disk**, **Instance type**, **Cmd template** fields |  |
| 8 | Click **Run** button |  |
| 9 | Click **Launch** button in the appeared pop-up |  |
| 10 | Click on the just-launched pipeline on the **ACTIVE RUNS** tab of the **Runs** page to open the **RUN LOGS** page |  |
| 11 | Wait until the **SSH** hyperlink appears in the right upper corner |  |
| 12 | Click the **SSH** hyperlink |  |
| 13 | In the opened tab, in the terminal run: `cd / && head test_file.txt` | A string appears with the text specified at step 5 of the [EPMCMBIBPC-694](EPMCMBIBPC-694.md) case |
| 14 | Close the SSH tab |  |

**After**:
- Stop the run
