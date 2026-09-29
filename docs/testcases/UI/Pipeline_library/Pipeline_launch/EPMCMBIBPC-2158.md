# Input data in pipeline validation

Test verifies that Input, Output and Common path parameters of a pipeline correctly read from and write to folders and files in a storage.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Create storage, open it |  |
| 3 | Create folder `in` |  |
| 4 | Create folder `out` |  |
| 5 | Create folder `common` |  |
| 6 | Create folder `common2` |  |
| 7 | Open the folder created at step 5 |  |
| 8 | Create non-empty files `common1.txt`, `common2.txt` |  |
| 9 | Open the folder created at step 6 |  |
| 10 | Create folder `common3` |  |
| 11 | Create non-empty files `common3.txt`, `common4.txt` |  |
| 12 | Open the folder created at step 3 |  |
| 13 | Create non-empty files `in.txt`, `notCopied.txt` |  |
| 14 | Open library |  |
| 15 | Create a pipeline (from the SHELL template) |  |
| 16 | Open the created pipeline, select version |  |
| 17 | On the pipeline page select **CODE** tab |  |
| 18 | Replace the code of the main file with the following code and save it:<br>`#!/usr/bin/env bash`<br>`pipe_log SUCCESS "Running shell pipeline" "Task1"`<br>`cp -r ${INPUT_DIR}/* ${ANALYSIS_DIR}`<br>`cp -r ${COMMON_DIR}/* ${ANALYSIS_DIR}` |  |
| 19 | Select **CONFIGURATION** tab |  |
| 20 | Hover over the **v** element to the right of the **Add parameters** button: select **Output path parameter** item, specify the parameter name in the left field of the appeared row, and specify the full path to the `out` folder created at step 4 in the right field |  |
| 21 | Hover over the **v** element to the right of the **Add parameters** button: select **Input path parameter** item, specify the parameter name in the left field, and specify the full path to the `in.txt` file created at step 13 in the right field |  |
| 22 | Hover over the **v** element to the right of the **Add parameters** button: select **Common path parameter** item, specify the parameter name in the left field, and specify the full path to the `common` folder created at step 5 and to the `common3.txt` file created at step 11 in the right field |  |
| 23 | Click **Save** button |  |
| 24 | Click **RUN** button |  |
| 25 | Click **Launch** button |  |
| 26 | Click **Launch** button in the appeared pop-up window |  |
| 27 | Wait for the pipeline run to finish |  |
| 28 | Open library, navigate to the `out` folder created at step 4 | The `out` folder contains: <li> the `common` folder containing the `common1.txt` file (created at step 8) and `common2.txt` file (created at step 8) <li> the `common2` folder containing the `common3.txt` file (created at step 11) <li> the `in` folder (created at step 3) containing the `in.txt` file (created at step 13); the `notCopied.txt` file (created at step 13) is not displayed |
