# [MANUAL] Check of add Boolean parameter

Test verifies that a Boolean-type launch parameter's checkbox and Enabled/Disabled label correctly reflect true/false values, and that the pipeline reads it correctly at runtime.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline from the SHELL template |  |
| 2 | Replace the pipeline code with the code from the attached file |  |
| 3 | Save and commit the changed file |  |
| 4 | Open the **CONFIGURATION** tab |  |
| 5 | Add a variable of the **Boolean** type with the name `true` |  |
| 6 | Make sure the checkbox is enabled |  |
| 7 | Make sure the label near the checkbox has the value `Enabled` |  |
| 8 | Add a variable of type **Output path parameter** |  |
| 9 | Enter a valid name and a valid path to a storage |  |
| 10 | Add a variable of the **Boolean** type with the name `false` |  |
| 11 | Disable the checkbox |  |
| 12 | Make sure the label near the checkbox has the value `Disabled` |  |
| 13 | Save the configuration |  |
| 14 | Launch the pipeline |  |
| 15 | Go to the running pipeline's page |  |
| 16 | Click **Parameters** | 3 parameters are displayed: <li> the value opposite the `true` parameter is `true` <li> the value opposite the `false` parameter is `false` |
| 17 | After the pipeline finishes, go to the path specified at step 9 | The file `bolleantest_passed.txt` is displayed at the path specified at step 9 |
