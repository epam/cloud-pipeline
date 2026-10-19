# Validation of commited docker

Test verifies that a file created via SSH before committing to a personal group is present in a run launched from that committed image.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-1352](EPMCMBIBPC-1352.md) case |  |
| 2 | Open **Tools** page |  |
| 3 | Select **Default registry** |  |
| 4 | Select the **personal** tools group |  |
| 5 | Click on the tool with the name specified at step 11 of the [EPMCMBIBPC-1352](EPMCMBIBPC-1352.md) case |  |
| 6 | Click **SETTINGS** tab |  |
| 7 | Check that valid values are set in **Port**, **Disk**, **Instance type**, **Cmd template** fields |  |
| 8 | Click **SAVE** button |  |
| 9 | Click **Run** button |  |
| 10 | Click **Launch** button in the appeared pop-up |  |
| 11 | Click on the just-launched pipeline on the **ACTIVE RUNS** tab of the **Runs** page to open the **RUN LOGS** page |  |
| 12 | Wait until the **SSH** hyperlink appears in the right upper corner |  |
| 13 | Click the **SSH** hyperlink |  |
| 14 | In the opened tab, in the terminal run: `cd / && head test_file.txt` | A string appears with the text specified at step 5 of the [EPMCMBIBPC-1352](EPMCMBIBPC-1352.md) case |

**After**:
- Stop the run
