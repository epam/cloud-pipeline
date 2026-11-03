# Check "CONFIGURATION" tab on pipeline edit page

Test verifies the initial layout of the Configuration tab for a new pipeline.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Hover over **+ Create v**, click **Pipeline** item |  |
| 3 | Specify a valid pipeline name in the appeared pop-up window |  |
| 4 | Click **CREATE** button |  |
| 5 | Click on the created pipeline |  |
| 6 | Click on the pipeline version |  |
| 7 | Click on **CONFIGURATION** tab | The configuration page opens, containing: <li> configuration tabs (by default only one exists - named `default`) <li> **+ ADD**, **Save** buttons <li> an editable field for changing the configuration name <li> the label **Estimated price per hour** with a value <li> the **Exec environment** collapsed header (minimized by default) <li> the **Advanced** collapsed header (minimized by default) <li> the **Parameters** collapsed header (expanded by default) <li> the **Add parameter** button in the **Parameters** section |
