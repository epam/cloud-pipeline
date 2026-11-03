# Add "language" parameter, for other languages

Test verifies that setting `language: other` in `config.json` hides the **GRAPH** tab.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the WDL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | On the pipeline page select **CODE** tab |  |
| 5 | Click on `config.json` file and edit it: in the `configuration` section set `"language": "other"`, save the edited file | After the page is refreshed, the **GRAPH** tab doesn't display |
