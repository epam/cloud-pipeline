# [MANUAL] SERVICES widget

Test verifies the SERVICES widget's record and controls for a launched tool's endpoint.

**Prerequisites**:
- There are no active runs at the beginning of the test

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Home** page |  |
| 2 | Click **Configure** in the upper-right corner |  |
| 3 | In the pop-up that appears, set the checkbox near **Services** and unset the others |  |
| 4 | Click **OK** | The message "There are no services" is displayed in the **SERVICES** widget |
| 5 | Open the **Tools** page |  |
| 6 | Select a registry, tools group, and a tool with an endpoint (e.g. `e2e-endpoints`) |  |
| 7 | Launch the selected tool |  |
| 8 | On the **Runs** page, click on the just-launched pipeline (save the run ID) |  |
| 9 | Wait until the **Endpoint** link appears in the upper-left corner |  |
| 10 | Wait 3 minutes |  |
| 11 | Repeat step 1 | A record appears in the **SERVICES** widget, containing: <li>the endpoint name for the tool selected at step 6 <li>labels with the registry, tools group and tool name selected at step 6 <li>a label with the run ID saved at step 8 |
| 12 | In the **SERVICES** widget, click on the endpoint name (for the tool selected at step 6) | The application from the tool launched at step 7 appears (for e2e-endpoints: the page with the user name appears) |
| 13 | Close the just-opened tab |  |
| 14 | Open the **Runs** page |  |
| 15 | Click **STOP** next to the run with the ID saved at step 8 |  |
| 16 | In the pop-up that appears, click **STOP** |  |
| 17 | Repeat step 1 | The message "There are no services" is displayed in the **SERVICES** widget |
