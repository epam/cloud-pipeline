# Run pipeline that have configured output in parameters run

Test verifies that an Output parameter path entered directly on the launch form saves the pipeline's output file to that path.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create an object storage |  |
| 3 | Create a pipeline (from the SHELL template) |  |
| 4 | Open the created pipeline, select version |  |
| 5 | On the pipeline page select **CODE** tab |  |
| 6 | Add a script for saving files into the code file (replace the code file with the attached one) |  |
| 7 | Click **RUN** button |  |
| 8 | Click **Add parameters** button |  |
| 9 | In the appeared row, specify a parameter name into the left field (e.g. `result`) |  |
| 10 | In the appeared row, specify into the right field a path like `{full path to the storage created at step 2}/storage_rules_folder/${RUN_ID}/` |  |
| 11 | Click **Launch** button |  |
| 12 | Wait for the pipeline to finish |  |
| 13 | Open the path specified at step 10 | The file created in the pipeline code (step 6) is displayed on the path specified at step 10 |
