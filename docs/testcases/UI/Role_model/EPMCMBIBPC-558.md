# Check file edit prohibition for read-only user

Test verifies that a read-only user does not see the EDIT button for a pipeline's config.json file.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-546](EPMCMBIBPC-546.md) case |  |
| 2 | Click on the pipeline |  |
| 3 | Click on the pipeline version |  |
| 4 | Click the **CODE** tab |  |
| 5 | Click on the `config.json` file | The **EDIT** button is not displayed |
