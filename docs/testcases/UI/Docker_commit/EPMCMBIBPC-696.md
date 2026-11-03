# Validation of "Stop pipeline" flag

Test verifies that committing with the Stop pipeline flag set stops the run after the commit finishes.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 2 | Click on `{registry_name}`, select **Default registry** |  |
| 3 | Click on `{group_name}`, select the group from step 3 of the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 4 | Specify a tool name equal to the tool selected at step 4 of the [EPMCMBIBPC-692](EPMCMBIBPC-692.md) case |  |
| 5 | Set the **Stop pipeline** checkbox |  |
| 6 | Click **COMMIT** button |  |
| 7 | Click **OK** button in the appeared pop-up | <li> the pop-up is closed <li> in the right upper corner, the label **COMMITING** appears <li> the commit finishes successfully <li> after the commit finishes, the pipeline is stopped |
