# Validation of version release

Test verifies releasing a pipeline version with a given name.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline |  |
| 4 | Click **RELEASE** button opposite the pipeline version |  |
| 5 | Enter a version name in the appeared pop-up window |  |
| 6 | Click **RELEASE** button | <li> the pipeline version name equals the one entered at step 5 <li> the **RELEASE** button opposite the pipeline version doesn't display |
