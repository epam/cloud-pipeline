# Search for identical names

Test verifies that a folder and an issue with the same name both surface in an unfiltered search, and how the per-type filter buttons narrow the results.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2670](EPMCMBIBPC-2670.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the folder created at step 2 of the EPMCMBIBPC-2653 case |  |
| 2 | Hover over **+ Create v**, click **Folder** |  |
| 3 | Specify a folder name equal to the issue title specified at step 4 of the EPMCMBIBPC-2670 case, click **OK** |  |
| 4 | Click the home icon in the left menu panel |  |
| 5 | Click the search icon in the left menu panel | The **FOLDERS**, **PIPELINES**, **RUNS**, **TOOLS**, **DATA**, **ISSUES** buttons are enabled |
| 6 | Enter into the search query the name specified at step 3 |  |
| 7 | Press **Enter** | <li>The **PIPELINES**, **RUNS**, **TOOLS**, **DATA** buttons are disabled <li>The **FOLDERS**, **ISSUES** buttons are enabled <li>The **FOLDERS** button's name changes to "1 FOLDER" <li>The **ISSUES** button's name changes to "1 ISSUE" <li>In the search result list, there are 2 items, both with the name specified at step 3 |
| 8 | Click **1 FOLDER** on the search panel | <li>The **1 FOLDER**, **1 ISSUE** buttons are enabled <li>The **1 FOLDER** button is active (selected) <li>In the search result list, there is 1 item, with the name specified at step 3 |
| 9 | Repeat step 8 | <li>The **1 FOLDER**, **1 ISSUE** buttons are enabled <li>In the search result list, there are 2 items, both with the name specified at step 3 |
| 10 | Press **Esc** |  |
| 11 | Click **ISSUES** on the search panel | <li>The **FOLDERS**, **PIPELINES**, **RUNS**, **TOOLS**, **DATA**, **ISSUES** buttons are enabled <li>The **ISSUES** button is active (selected) |
| 12 | Enter into the search query the name specified at step 3 |  |
| 13 | Press **Enter** | <li>The **FOLDERS**, **PIPELINES**, **RUNS**, **TOOLS**, **DATA** buttons are disabled <li>The **ISSUES** button is enabled <li>The **ISSUES** button's name changes to "1 ISSUE" <li>The **1 ISSUE** button is active (selected) <li>In the search result list, there is 1 item, with the name specified at step 3 |
| 14 | Click **1 ISSUE** on the search panel | <li>The **FOLDERS**, **PIPELINES**, **RUNS**, **TOOLS**, **DATA** buttons are disabled <li>The **1 FOLDER**, **1 ISSUE** buttons are enabled <li>In the search result list, there are 2 items, both with the name specified at step 3 |
