# Validation of released non newest version of pipeline

Test verifies that a released, non-latest pipeline version has all editing controls hidden.

**Prerequisites**:
- Perform the [EPMCMBIBPC-383](EPMCMBIBPC-383.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click on the pipeline name from the [EPMCMBIBPC-383](EPMCMBIBPC-383.md) case in the library-tree on the left panel |  |
| 2 | Click on the released version |  |
| 3 | On the pipeline page select **CODE** tab | Edit buttons for files don't display |
| 4 | Click on the `config.json` file | The **EDIT** button doesn't display |
| 5 | Click **CLOSE** button |  |
| 6 | Select **DOCUMENTS** tab | **EDIT**, **Rename**, **Delete**, **Upload** buttons don't display |
