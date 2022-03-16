# Check Pipe CLI Admin Run As User

Test verifies that an admin can launch a run as a different user using pipe CLI.

**Prerequisites**:
- Admin user
- Regular user

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as the admin user from the prerequisites | |
| 2 | Open the **Tools** page | |
| 3 | Select test tool | |
| 4 | Run tool with *Custom settings* | |
| 5 | At the Runs page, click the just-launched run | |
| 6 | Wait until the **SSH** hyperlink appears | |
| 7 | Click the **SSH** hyperlink | |
| 8 | In the opened tab, enter and perform the `pipe run -di <tool>:latest -u <REGULAR USER> -y` command, where `<tool>` is any tool name with group, `<REGULAR USER>` is the Regular user from the Prerequisites | The output contains `Pipeline run scheduled with RunId: <runID>` |
| 9 | Store `runID` from output | | 
| 10 | Open the **Runs** page | |
| 11 | Click the run with *ID* stored at step 9 | <li> The owner of the run is USER3. <li> The run does not have *ORIGINAL_OWNER* parameter specified. |

**After:**
- Stop the runs launched at steps 4 and 8
