# [MANUAL] PROJECTS widget

Test verifies the PROJECTS widget's record and controls for a created project, including its history and data storage.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Hover over **+ Create v**, click **PROJECT** |  |
| 3 | Specify a valid project name, click **OK** |  |
| 4 | Open the **Home** page |  |
| 5 | Click **Configure** in the upper-right corner |  |
| 6 | In the pop-up that appears, set the checkbox near **Projects** and unset the others |  |
| 7 | Click **OK** | At least 1 record appears in the **PROJECTS** widget, containing: <li>a header with the project name specified at step 3 <li>labels: "type" - "project"; "ProjectID" - "{project name}"; "DataStorage" - the full path to the project storage, containing "...{project name}-storage" |
| 8 | Hover over the record with the project name specified at step 3 | The **HISTORY** and **DATA STORAGE** buttons appear |
| 9 | Repeat step 8 and click on the project name | The folder of the project created at step 3 opens |
| 10 | Repeat step 4 |  |
| 11 | Repeat step 8 and click **HISTORY** | <li>The runs history page of the project created at step 3 opens <li>The message "No Data" is displayed |
| 12 | Repeat step 4 |  |
| 13 | Repeat step 8 and click **DATA STORAGE** | <li>The data storage "{project name}-storage" of the project created at step 3 opens <li>The message "Folder is empty" is displayed |
