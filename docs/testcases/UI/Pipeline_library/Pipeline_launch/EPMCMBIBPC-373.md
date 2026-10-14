# Add "language" parameter, for LUIGI

Test verifies that setting `language: luigi` in `config.json` makes the **GRAPH** tab appear.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | On the pipeline page select **CODE** tab |  |
| 5 | Click on `config.json` file and edit it: in the `configuration` section set `"language": "luigi"`, save the edited file | After the page is refreshed, the **GRAPH** tab appears |
