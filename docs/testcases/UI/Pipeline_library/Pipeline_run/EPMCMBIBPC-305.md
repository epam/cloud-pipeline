# Pipeline log

Test verifies the run log page's header, owner label, and initial task list for a launched pipeline.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-100](EPMCMBIBPC-100.md) case |  |
| 2 | Click on the **LOG** button opposite the just-launched pipeline from step 1 |  |
| 3 | Wait for the pipeline run to finish | The run log page is displayed, containing: <li> a header with the pipeline id and name equal to the id and name of the pipeline from step 1 <li> the label **Owner** with the user name that started the pipeline <li> in the left column, two tasks: the first named **Task1**, the second named after the pipeline from step 1 |
