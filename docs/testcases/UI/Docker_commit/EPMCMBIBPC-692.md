# Push docker form validation

Test verifies the content of the Commit pipeline run pop-up opened from a launched tool.

**Prerequisites**:
- The user or their group should have READ and WRITE permissions to the registry
- The user or their group should have READ, WRITE and EXECUTE permissions to the tools group

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default registry** |  |
| 3 | Select a tools group from the prerequisites |  |
| 4 | Click on the endpoint tool |  |
| 5 | Click **Settings** tab |  |
| 6 | Check that valid values are set in **Port**, **Disk**, **Instance type**, **Cmd template** fields |  |
| 7 | Click **Run** button |  |
| 8 | Click **Launch** button in the appeared pop-up |  |
| 9 | Click on the just-launched pipeline on the **ACTIVE RUNS** tab of the **Runs** page to open the **RUN LOGS** page |  |
| 10 | Wait until the **COMMIT** hyperlink appears in the right upper corner |  |
| 11 | Click on the **COMMIT** hyperlink | A pop-up with the header `Commit pipeline run` appears, containing: <li> the **Docker image** field with the full docker image name in the form `{registry_name}/{group_name}/{docker_image_name}` <li> the **Version** field opposite the **Docker image** field <li> the **Delete runtime files** checkbox <li> the **Stop pipeline** checkbox <li> **CANCEL**, **COMMIT** buttons |
