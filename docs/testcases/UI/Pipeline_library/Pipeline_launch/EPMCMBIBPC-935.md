# Validation of several paths in parameter

Test verifies that an Output path parameter configured with two selected folders saves the pipeline's output file into both of them.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create a pipeline (from the SHELL template) |  |
| 3 | Open the created pipeline, select version |  |
| 4 | On the pipeline page select **CODE** tab |  |
| 5 | Add a script for saving files into the code file (replace the code file with the attached one) |  |
| 6 | Click **RUN** button |  |
| 7 | Hover over the **v** element to the right of the **Add parameters** button: select **Output path parameter** item; specify the parameter name in the left field; click the grey button of the right field; in the appeared pop-up window select an existing data storage in the library-tree on the left panel; check the checkbox opposite a folder name on the right panel; check the checkbox opposite another folder name; click **OK** button |  |
| 8 | Click **Launch** button |  |
| 9 | Wait for the pipeline to finish |  |
| 10 | Open the storage selected at step 7 |  |
| 11 | Navigate to the folders selected at step 7 | The file created in the pipeline code (step 5) is displayed in the folders selected at step 7 |
