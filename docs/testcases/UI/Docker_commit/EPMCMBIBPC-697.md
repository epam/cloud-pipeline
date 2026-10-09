# Validation of commited docker that had enabled "Delete runtime files" flag

Test verifies that a tool committed with Delete runtime files still starts correctly.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-724](EPMCMBIBPC-724.md) case |  |
| 2 | Open **Tools** page |  |
| 3 | Select **Default registry** |  |
| 4 | Select the tools group specified at step 3 of the [EPMCMBIBPC-724](EPMCMBIBPC-724.md) case |  |
| 5 | Click on the tool specified at step 4 of the [EPMCMBIBPC-724](EPMCMBIBPC-724.md) case |  |
| 6 | Click **SETTINGS** tab |  |
| 7 | Check that valid values are set in **Port**, **Disk**, **Instance type**, **Cmd template** fields |  |
| 8 | Click **Run** button |  |
| 9 | Click **Launch** button in the appeared pop-up | The tool starts without errors |
