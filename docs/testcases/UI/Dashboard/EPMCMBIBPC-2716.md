# [MANUAL] TOOLS widget

Test verifies the TOOLS widget's record and controls for a tool committed into a personal tools group.

**Prerequisites**:
- Login as admin
- Open the **Tools** page and create a personal tools group if it doesn't exist

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Tools** page, select a tool (e.g. `ubuntu`) and launch it (save the tool's short description) |  |
| 2 | On the **Runs** page, click on the just-launched pipeline |  |
| 3 | Wait until the **SSH** button appears in the upper-right corner |  |
| 4 | Click **COMMIT** |  |
| 5 | In the pop-up that appears, select a personal tools group and specify a valid tool name |  |
| 6 | Click **COMMIT** |  |
| 7 | Wait until the "COMMIT SUCCEEDED" label appears |  |
| 8 | Click **STOP** |  |
| 9 | In the pop-up that appears, click **STOP** |  |
| 10 | Open the **Home** page |  |
| 11 | Click **Configure** in the upper-right corner |  |
| 12 | In the pop-up that appears, set the checkbox near **Tools** and unset the others |  |
| 13 | Click **OK** | At least 1 record appears in the **TOOLS** widget, containing: <li>the tool name specified at step 5 <li>the short description from the tool saved at step 1 <li>labels: "Default registry"; "{personal tools group name}" equal to the admin name from prerequisite step 1 |
| 14 | Hover over the record with the tool name specified at step 5 | The **RUN** button appears |
| 15 | Repeat step 14 and click on the tool name | The page of the tool with the name specified at step 5 opens |
| 16 | Repeat step 10 |  |
| 17 | Repeat step 14 and click **RUN** | <li>A pop-up "Run {tool name} with default settings?" appears, where {tool name} is the one specified at step 5 <li>The pop-up contains the **Run custom**, **Cancel**, **RUN** buttons |
