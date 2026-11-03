# Validation of friendly URL

Test verifies that a run launched with a Friendly URL is reachable at an endpoint address ending in that string.

**Prerequisites**:
- An existing tool with an endpoint (e.g. `e2e-endpoints`)

**Preparations**:
1. Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select a registry, group, click the tool from the prerequisites |  |
| 3 | Hover over the **v** button near the **Run** button |  |
| 4 | Click **Custom settings** in the appeared list |  |
| 5 | On the launch form expand the **Exec environment** and **Advanced** sections |  |
| 6 | Check that valid values are set in **Node type** and **Disk (Gb)** |  |
| 7 | Input a valid string into **Friendly URL** (e.g. `tool_page`) |  |
| 8 | Click **Launch** button |  |
| 9 | Click **Launch** button in the appeared pop-up |  |
| 10 | Click the just-launched run on the **ACTIVE RUNS** tab |  |
| 11 | Wait until the hyperlink near the **Endpoint** label appears |  |
| 12 | Wait 3 minutes |  |
| 13 | Click the **Endpoint** hyperlink | <li> a new tab with a started application from the tool appears (for e2e-endpoints: the page with the user name who launched the tool appears) <li> the link in the address bar ends with the string entered at step 7 |
| 14 | Close the appeared tab, return to the run logs page opened at step 10 |  |
