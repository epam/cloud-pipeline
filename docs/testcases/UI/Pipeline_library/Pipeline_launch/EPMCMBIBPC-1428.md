# [MANUAL] Run pipeline that have two configured output in parameters by UI

Test verifies that an Output path parameter configured with two selected directories saves the pipeline's output file into both of them.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline |  |
| 2 | Add to the pipeline code a script that saves files to disk (see the attached file) |  |
| 3 | Click the **RUN** button |  |
| 4 | Click the dropdown arrow to the right of the **Add parameters** button, select **Output path parameter**; in the appeared row, enter the parameter name into the first field; click the button on the left side of the row; in the appeared pop-up, select the data storage in the tree on the left panel; on the right panel, select two directories |  |
| 5 | Click **Launch** |  |
| 6 | Wait for it to finish |  |
| 7 | Open the Data Storage |  |
| 8 | Navigate to the path specified for saving files in the launch parameter | A file is displayed in each of the selected folders |
