# Run tool with custom settings validation (EXECUTION DEFAULTS aren't filled)

Test verifies launching a tool with no execution defaults filled in, using custom settings entered at launch time.

**Prerequisites**:
- User can execute at least one empty tool of a group

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Select a group |  |
| 4 | Click a tool that is executable for the user |  |
| 5 | Click **SETTINGS** tab |  |
| 6 | Expand **EXECUTION ENVIRONMENT** section if it's minimized |  |
| 7 | Check whether **Instance type**, **Disk (Gb)** and **Cmd template** fields are empty |  |
| 8 | Click the **v** button on the right of the **Run** button |  |
| 9 | Click **Custom settings** in the appeared dropdown list | The **Launch** page appears that contains: <li> the sign `Launch {tool_name} {tool_version_name}` <li> the **Docker image** field shows the image name as `{registry_address}/{group_name}/{tool_name}:{version_name}` <li> the **Cmd template** field is empty <li> **Node type**, **Disk (Gb)** fields are empty <li> a dash is displayed near the estimated price label |
| 10 | Input valid values into the **Node type**, **Disk (Gb)** fields |  |
| 11 | Click the **Price type** combobox and select **On-demand** item |  |
| 12 | Input a natural number into the **Timeout (min)** field |  |
| 13 | Input a valid value into the **Cmd template** field |  |
| 14 | Click **Launch** button |  |
| 15 | Click **Launch** button in the appeared pop-up | The run starts |
| 16 | Click the just-launched run on the **ACTIVE RUNS** page |  |
| 17 | Click the **Instance** collapsed header | <li> the **Node type**, **Cmd template**, **Price type** fields show the values entered at steps 10, 11, 13 <li> the run stops after the number of minutes entered at step 12 |
