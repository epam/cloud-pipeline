# Validation of commit pipeline as docker

Test verifies committing a running pipeline as a new docker tool.

**Preparations**:
1. Login as admin
2. Open **Tools** page
3. Select **Default registry**
4. Select a group
5. Click the `shell` tool if it exists
6. Click the gear icon
7. Click **Delete tool** item
8. Click **OK** button

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Library** page |  |
| 2 | Click **+ Create v** button |  |
| 3 | In the appeared list select **Pipeline** item -> click the **SHELL** template item |  |
| 4 | Specify a valid pipeline name in the appeared window |  |
| 5 | Click **CREATE** button |  |
| 6 | Click on the just-created pipeline |  |
| 7 | Click on the pipeline version |  |
| 8 | Click **CODE** tab |  |
| 9 | Click on the `*.sh` file |  |
| 10 | Click **EDIT** button |  |
| 11 | Add the following row: `sleep 10000` |  |
| 12 | Click **SAVE** button |  |
| 13 | Specify a commit message in the appeared pop-up and click **Commit** button |  |
| 14 | Click **RUN** button |  |
| 15 | Specify valid values on the launch page |  |
| 16 | Click **Launch** button |  |
| 17 | Click **Launch** button in the appeared pop-up |  |
| 18 | Click on the just-launched pipeline on the **ACTIVE RUNS** tab of the **Runs** page to open the **RUN LOGS** page |  |
| 19 | Wait until the **COMMIT** hyperlink appears in the right upper corner |  |
| 20 | Click on the **COMMIT** hyperlink |  |
| 21 | Click on `{registry_name}`, select **Default registry** |  |
| 22 | Click on `{group_name}`, select a tools group from the Preparation section |  |
| 23 | Input a name for the docker image - `shell` |  |
| 24 | Click **COMMIT** button | <li> the pop-up is closed <li> in the right upper corner, the label **COMMITING** appears <li> the commit finishes successfully |
