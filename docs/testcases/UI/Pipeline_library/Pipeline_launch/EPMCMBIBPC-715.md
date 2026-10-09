# Validation of pipeline release

Test verifies releasing a pipeline version, and the read-only file controls shown for the released version.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline |  |
| 4 | Click on the version |  |
| 5 | Click on the pipeline name from step 2 in the library-tree on the left panel |  |
| 6 | Click **RELEASE** button opposite the pipeline version |  |
| 7 | Enter a version name in the appeared pop-up window |  |
| 8 | Click **RELEASE** button |  |
| 9 | Click on the released version |  |
| 10 | Select **CODE** tab | The `+`, `+ NEW FILE`, `Upload` buttons are displayed above the files list; **Rename**, **Delete** buttons are displayed opposite the files |
| 11 | Click on the `config.json` file | **EDIT**, **CLOSE** buttons are displayed |
| 12 | Click **CLOSE** button |  |
| 13 | Select **DOCUMENTS** tab | The **Upload** button is displayed above the files list; **Delete**, **Rename**, **Download** buttons are displayed opposite the files |
