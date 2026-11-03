# Check file editing for released pipeline

Test verifies that editing `config.json` on a released pipeline creates a new version carrying the edit.

**Prerequisites**:
- Perform the [EPMCMBIBPC-380](EPMCMBIBPC-380.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the pipeline from the [EPMCMBIBPC-380](EPMCMBIBPC-380.md) case, select version |  |
| 2 | On the pipeline page select **CODE** tab |  |
| 3 | Click on `config.json` file and edit it: add some valid JSON code, save the edited file |  |
| 4 | Click on the pipeline name in the library-tree on the left panel | A new version appears in the pipeline's versions list |
| 5 | Click on a new pipeline version |  |
| 6 | On the pipeline page select **CODE** tab |  |
| 7 | Click on `config.json` file | The changes saved at step 3 are displayed in the `config.json` file |
