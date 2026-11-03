# Prepare for search

Test creates a folder with a pipeline, a detached configuration and a subfolder, and opens the Global search form, as shared setup for the search cases.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Create a folder, open it |  |
| 3 | Hover over **+ Create v**, click **Pipeline** |  |
| 4 | Specify a valid pipeline name, click **CREATE** |  |
| 5 | Hover over **+ Create v**, click **Configuration** |  |
| 6 | Specify a valid configuration name, click **CREATE** |  |
| 7 | Hover over **+ Create v**, click **Folder** |  |
| 8 | Specify a valid folder name (be sure to use both upper- and lowercase), click **OK** |  |
| 9 | Open the detached configuration object created at step 6 |  |
| 10 | Change the value of the **Name** field (specify a value other than `default`) |  |
| 11 | Set valid values for **Docker image**, **Node type**, **Disk (Gb)** |  |
| 12 | Click **Save** |  |
| 13 | Navigate to the folder created at step 8 |  |
| 14 | Repeat steps 7-8 |  |
| 15 | Click the home icon in the left menu panel |  |
| 16 | Press **Ctrl+F** | The **Global_search** form appears, containing: <li>the **FOLDERS**, **PIPELINES**, **RUNS**, **TOOLS**, **DATA**, **ISSUES** buttons, enabled <li>the search query bar <li>a question-mark icon to the left of the search bar |
| 17 | Press **Esc** | The Cloud Pipeline home page opens |
| 18 | Click the search icon in the left menu panel | The **Global_search** form appears, containing: <li>the **FOLDERS**, **PIPELINES**, **RUNS**, **TOOLS**, **DATA**, **ISSUES** buttons, enabled <li>the search query bar <li>a question-mark icon to the left of the search bar |
| 19 | Hover over the question-mark icon to the left of the search query bar | A tooltip appears with the text "The query string supports the following special characters <...>" |
