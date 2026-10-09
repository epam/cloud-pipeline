# Validation of Singularity launch

Test verifies that the `CP_CAP_SINGULARITY` system parameter is applied and enables the `singularity` command inside the launched tool.

**Prerequisites**:
- The user must be able to run tools

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Tools** page |  |
| 2 | Select a registry, tool group and open a tool (e.g. `Ubuntu`) |  |
| 3 | Click the **v** button near the **Run** button, select **Custom settings** in the list |  |
| 4 | On the launch page, expand the **Advanced** section |  |
| 5 | Click the **Add system parameter** button |  |
| 6 | Find `CP_CAP_SINGULARITY` in the list |  |
| 7 | Select the found parameter |  |
| 8 | Click the **OK (1)** button | In the **Advanced** section, an enabled checkbox `CP_CAP_SINGULARITY` appears |
| 9 | Launch the tool |  |
| 10 | Open the **Run logs** page |  |
| 11 | Expand the **Parameters** section | The **Parameters** section shows the parameter `CP_CAP_SINGULARITY: true` |
| 12 | Wait until the **SSH** button appears in the right upper corner |  |
| 13 | Click the **SSH** button |  |
| 14 | In the opened tab, run the command: `singularity help version` | The command output contains the text `Show the version for Singularity...` |
