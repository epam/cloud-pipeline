# Validation of view all draft versions history of a pipeline

Test verifies that the HISTORY tab's **View all versions history** shows runs across all draft versions, not just the currently selected one.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Click **+ Create v**, in the appeared list click **Pipeline** item |  |
| 3 | Specify a valid pipeline name in the appeared window, click **Create** button |  |
| 4 | Open the created pipeline |  |
| 5 | Store a version name |  |
| 6 | Click **RUN** button |  |
| 7 | Click **Launch** button |  |
| 8 | Click **Launch** button in the appeared pop-up window |  |
| 9 | On the appeared page click **STOP** hyperlink opposite the launched pipeline |  |
| 10 | Click **STOP** button in the appeared window |  |
| 11 | Open library |  |
| 12 | Click on the pipeline created at step 3 |  |
| 13 | Click on **RELEASE** button opposite the pipeline version whose name was stored at step 5 |  |
| 14 | Specify a valid pipeline version name in the appeared window |  |
| 15 | Click **RELEASE** button |  |
| 16 | Click **RUN** button opposite the just-released pipeline version |  |
| 17 | Repeat steps 7-12 |  |
| 18 | Click on the pipeline version |  |
| 19 | Select **CODE** tab |  |
| 20 | Click on the main code file, edit it (e.g. add `sleep 1`) |  |
| 21 | Click **SAVE** button |  |
| 22 | Specify a valid commit message in the appeared pop-up window |  |
| 23 | Click **Commit** button |  |
| 24 | Store a new version name |  |
| 25 | Repeat steps 6-12 |  |
| 26 | Click on the pipeline version whose name was stored at step 24 |  |
| 27 | Click on **HISTORY** tab | In the runs list there is 1 run, its version name equals the name stored at step 24 |
| 28 | Click on **View all versions history** | In the runs list there are 3 runs, their version names equal the names stored at steps 5, 14, 24 |
