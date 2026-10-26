# [MANUAL] Validation of keyboard settings modification

Test verifies that a NoMachine session's keyboard layout can be changed and that Ctrl+Shift cycles between the configured layouts.

**Prerequisites**:
- The user must have execute access to at least one NoMachine tool (e.g. `ubuntu-nomachine`)
- The NoMachine client application must be pre-installed

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | In the search bar, specify the tool name from the prerequisites, click the appeared tile with the tool name |  |
| 3 | Click the **Run** button |  |
| 4 | Click the **Launch** button to confirm |  |
| 5 | In the **ACTIVE RUNS** tab click the just-launched pipeline |  |
| 6 | Wait until the **Endpoint** field appears in the left upper corner |  |
| 7 | Click the **Endpoint** hyperlink | The `cloud-service-<run_number>.nxs` file starts downloading |
| 8 | Click the downloaded `cloud-service-<run_number>.nxs` file to open it via the NoMachine application |  |
| 9 | Confirm all suggestions and wait until the Desktop is loaded |  |
| 10 | Right-click in any empty space of the Desktop |  |
| 11 | In the appeared dropdown menu select **Applications** -> **Settings** -> **Keyboard** |  |
| 12 | In the **Keyboard** popup click the **Layout** tab | The keyboard layout list appears with the items: **English (US)**, **French**, **German** |
| 13 | Click the **Close** button |  |
| 14 | Repeat step 10 |  |
| 15 | In the appeared dropdown menu select **Open Terminal Here** |  |
| 16 | In the terminal press keys `q`, `a`, `z` | The string `qaz` appears in the terminal |
| 17 | In the terminal press the **Enter** key |  |
| 18 | In the terminal press the **Ctrl+Shift** combination |  |
| 19 | Repeat step 16 | The string `aqw` appears in the terminal |
| 20 | Repeat steps 17-19 | The string `qay` appears in the terminal |
| 21 | Repeat step 20 | The string `qaz` appears in the terminal |

**After**:
- Close the NoMachine application and stop the run launched at step 4
