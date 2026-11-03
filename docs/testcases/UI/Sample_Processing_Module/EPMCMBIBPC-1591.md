# Pipeline preparation

Test creates a SHELL pipeline wired to the e2e-endpoints tool, and links it into the detached configuration's Pipeline field.

**Prerequisites**:
- The `e2e-endpoints` tool

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Hover over **+ Create v** |  |
| 3 | In the list that appears, select **Pipeline** -> click the **SHELL** template item |  |
| 4 | In the pop-up that appears, enter a valid pipeline name, click **CREATE** |  |
| 5 | Open the created pipeline, select the version, click the **CODE** tab |  |
| 6 | Click **Delete** opposite `main_file`, click **OK** in the pop-up that appears |  |
| 7 | Click **Upload**, upload the `.sh` file from the attachments |  |
| 8 | Click on the `config.json` file, click **EDIT** |  |
| 9 | Change the `config.json` content to the attached one (see the attached `.json` file) and specify the path and group to the `e2e-endpoint` tool, click **SAVE**, specify a commit message and click **Commit** |  |
| 10 | Open the detached configuration created in the [EPMCMBIBPC-1588](EPMCMBIBPC-1588.md) case |  |
| 11 | Expand the **Exec environment** section, in the **Pipeline** field select the pipeline created at step 4 |  |
| 12 | Click **Save** | The pipeline's parameters are displayed |
