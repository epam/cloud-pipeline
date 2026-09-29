# [MANUAL] Rerurn pipeline

Test verifies that RERUN opens the launch form pre-filled for the completed run's pipeline type and version.

**Prerequisites**:
- At least one completed pipeline

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Go to the runs tab |  |
| 2 | Open the **completed runs** tab |  |
| 3 | Click the **RERUN** hyperlink | The settings selection form for the pipeline's type and version opens. It contains: <li> the instance parameters the pipeline will run on <li> the pipeline launch string <li> additional parameters |
