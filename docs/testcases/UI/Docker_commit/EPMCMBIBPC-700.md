# Validation of committed pipeline

Test verifies that a tool built from a committed pipeline starts correctly with a custom Cmd template.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-698](EPMCMBIBPC-698.md) case |  |
| 2 | Open **Tools** page |  |
| 3 | Select **Default registry** |  |
| 4 | Select the tools group selected at step 22 of the [EPMCMBIBPC-698](EPMCMBIBPC-698.md) case |  |
| 5 | Click on the tool specified at step 23 of the [EPMCMBIBPC-698](EPMCMBIBPC-698.md) case |  |
| 6 | Click **SETTINGS** tab |  |
| 7 | Check that valid values are set in **Disk**, **Instance type**, **Price type** fields |  |
| 8 | Input `sleep 100` into the **Cmd template** field |  |
| 9 | Click **SAVE** button |  |
| 10 | Click **Run** button |  |
| 11 | Click **Launch** button in the appeared pop-up | The tool starts without errors |
