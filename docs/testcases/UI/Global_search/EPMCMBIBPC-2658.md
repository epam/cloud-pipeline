# Search for pipeline

Test verifies searching for a pipeline by name, including its README and config.json content, and navigating to the right tab from each result.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the folder created at step 2 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 2 | Click on the pipeline created at step 4 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 3 | Click on the pipeline version |  |
| 4 | Click the **CONFIGURATION** tab |  |
| 5 | Change the value of the **Name** field (specify a value other than `default` for the configuration name, e.g. `new_conf`) |  |
| 6 | Expand the **Exec environment** section, change the **Node type** and **Disk (Gb)** values |  |
| 7 | Click **Save** |  |
| 8 | Save the pipeline version name |  |
| 9 | Click the home icon in the left menu panel |  |
| 10 | Click the search icon in the left menu panel |  |
| 11 | Enter into the search query the pipeline name specified at step 4 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 12 | Press **Enter** | <li>A list with the search results appears containing 3 items <li>The **PIPELINES** button's name changes to "3 PIPELINES" <li>The **FOLDERS**, **RUNS**, **TOOLS**, **DATA**, **ISSUES** buttons are disabled |
| 13 | Hover in the list that appears over the found item with the name entered at step 11 | A content panel appears, containing: <li>the pipeline name equal to the name entered at step 11 <li>the **Found in name** field <li>the name of the pipeline version saved at step 8 |
| 14 | Hover in the list that appears over the found item named `docs/README.md` | A content panel appears, containing: <li>a header with the text "README.md" <li>the pipeline name equal to the name entered at step 11 <li>the **Found in pipelineName** field <li>the content of the `README.md` file |
| 15 | Hover in the list that appears over the found item named `config.json` | A content panel appears, containing: <li>a header with the text "config.json" <li>the pipeline name equal to the name entered at step 11 <li>the **Found in pipelineName** field <li>the content of the `config.json` file, containing: the configuration name specified at step 5; the node type and disk size specified at step 6 |
| 16 | Click on the found item with the name entered at step 11 | The pipeline created at step 4 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case opens (the page with pipeline versions) |
| 17 | Repeat step 10 |  |
| 18 | Click on the found item named `docs/README.md` | The **DOCUMENTS** tab of the pipeline created at step 4 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case opens |
| 19 | Repeat step 10 |  |
| 20 | Click on the found item named `config.json` | The **CODE** tab of the pipeline created at step 4 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case opens |
