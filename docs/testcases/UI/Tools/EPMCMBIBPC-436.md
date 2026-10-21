# Run tool with custom settings validation (EXECUTION DEFAULTS are filled)

Test verifies launching a tool with execution defaults already filled in, using custom settings that show the defaults pre-filled.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-429](EPMCMBIBPC-429.md) case |  |
| 2 | Perform the [EPMCMBIBPC-430](EPMCMBIBPC-430.md) case for the same tool used at step 1 |  |
| 3 | Click the **v** button near the **Run** button |  |
| 4 | Click **Custom settings** in the appeared list | The **Launch** page appears that contains: <li> **Exec environment**, **Advanced**, **Parameters** sections <li> **Exec environment** and **Advanced** sections are minimized <li> short information with the values from the **Exec environment** and **Advanced** sections fields is displayed opposite the section headers |
| 5 | Expand **Exec environment** section | <li> the **Docker image** field shows the image name as `{registry_address}/{group_name}/{tool_name}:{version_name}` <li> the **Cmd template** field shows the value equal to the **Cmd template** value entered in the [EPMCMBIBPC-430](EPMCMBIBPC-430.md) case <li> the **Node type**, **Disk (Gb)** fields show the values equal to the **Instance type** and **Disk (Gb)** values entered in the [EPMCMBIBPC-429](EPMCMBIBPC-429.md) case |
| 6 | Expand **Advanced** section |  |
| 7 | Click the **Price type** dropdown list and select **On-demand** item |  |
| 8 | Input a natural number into the **Timeout (min)** field (e.g. `2`) |  |
| 9 | Click **Launch** button |  |
| 10 | Click **Launch** button in the appeared pop-up | The run starts |
| 11 | Click the just-launched run on the **ACTIVE RUNS** page |  |
| 12 | Click the **Instance** collapsed header | <li> the **Price type** field shows the value selected at step 7 <li> the run stops after the number of minutes entered at step 8 |
