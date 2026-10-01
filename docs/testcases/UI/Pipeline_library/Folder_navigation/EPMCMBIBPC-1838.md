# [MANUAL] Check of attributes and description displaying for pipeline

Test verifies that a description and custom attributes set on a pipeline are shown under its name when viewed from the parent folder.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create a pipeline |  |
| 2 | Go into the pipeline |  |
| 3 | Click the gear icon |  |
| 4 | Enter a string into the **Pipeline description** field |  |
| 5 | Click the **SAVE** button |  |
| 6 | Click the **display-attributes** button |  |
| 7 | Click **Show attributes** |  |
| 8 | Click the **+ Add** button |  |
| 9 | Enter values into the **Key** and **Value** fields |  |
| 10 | Click the **Add** button |  |
| 11 | Repeat steps 8-10 five times |  |
| 12 | Go to the folder in which the pipeline was created at step 1 | <li> under the pipeline name, the value entered into the **Pipeline description** field is displayed <li> under the pipeline description, the values entered at steps 9-10 are displayed in columns (the **Value** field's value is displayed under the **Key** field's value) |
