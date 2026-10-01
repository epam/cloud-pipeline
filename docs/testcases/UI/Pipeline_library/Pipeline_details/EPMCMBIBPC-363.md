# Storage rules test

Test verifies that a storage rule's file mask correctly filters which output file is kept in the target folder.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Click **+ Create v**, in the appeared list click **Pipeline** item |  |
| 3 | Specify a valid pipeline name in the appeared window, click **Create** button |  |
| 4 | Open the created pipeline, select a version |  |
| 5 | On the pipeline page select **CODE** tab |  |
| 6 | Add a script for saving 2 files with different extensions into the code file (replace the content of the code file with the attached one), save it |  |
| 7 | Click on `config.json` file and edit it: add the following code into the `parameters` section, save the edited file:<br>`"result":`<br>`{`<br>`    "type": "output",`<br>`    "value": "{full path to the folder where result files will be saved after run finish}",`<br>`    "required": "true"`<br>`}` |  |
| 8 | Click on **STORAGE RULES** tab |  |
| 9 | Click **Delete** hyperlink opposite the default rule |  |
| 10 | Click **OK** button in the appeared pop-up window |  |
| 11 | Click **Add new rule** button |  |
| 12 | Specify a rule in the **File mask** field for one of the files that will be created during the pipeline run (e.g. `*.test`) |  |
| 13 | Set the **Move to STS** checkbox |  |
| 14 | Click **Create** button |  |
| 15 | Click **RUN** button |  |
| 16 | Click **Launch** button |  |
| 17 | Click **Launch** button in the appeared pop-up window |  |
| 18 | Wait for the pipeline run to finish |  |
| 19 | Open library, navigate to the path specified at step 7 | Only the file that satisfies the rule specified at step 12 is displayed |
