# [MANUAL] Validate of active runs switcher

Test verifies that the **Active runs** widget's owner switcher toggles between showing only the current user's runs and all users' runs.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as a non-admin user |  |
| 2 | Launch a pipeline (**Cmd template** - `Start idle`) |  |
| 3 | Login as admin |  |
| 4 | Launch a pipeline (**Cmd template** - `Start idle`) |  |
| 5 | Go to the **Active runs** page | The pipeline launched at step 4 is displayed |
| 6 | Click **View other available active runs** | The pipelines launched at steps 2 and 4 are displayed |
| 7 | Click **View only your active runs** | The pipeline launched at step 4 is displayed |
| 8 | Stop the pipelines launched at steps 2 and 4 |  |
