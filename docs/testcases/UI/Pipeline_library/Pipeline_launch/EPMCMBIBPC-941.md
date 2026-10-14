# Validation of button for all supported parameters types

Test verifies that Output, Path, Input path and Common path parameters set via the launch form's browse dialog all correctly save the pipeline's output file to the selected folder.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | On the pipeline page select **CODE** tab |  |
| 5 | Add a script for saving files into the code file (replace the code file with the attached one) |  |
| 6 | Click **RUN** button |  |
| 7 | Hover over the **v** element to the right of the **Add parameters** button: select **Output path parameter** item; specify the parameter name in the left field of the appeared row; click the grey button of the right field; in the appeared pop-up window select an existing data storage in the library-tree on the left panel; select a folder on the right panel; click **OK** button |  |
| 8 | Repeat step 7 for the **Path parameter** |  |
| 9 | Repeat step 7 for the **Input path parameter** |  |
| 10 | Repeat step 7 for the **Common path parameter** |  |
| 11 | Click **Launch** button |  |
| 12 | Wait for the pipeline to finish |  |
| 13 | Open the storage and folder selected at step 7 | The file created in the pipeline code (step 5) is displayed in the folder selected at step 7 |
