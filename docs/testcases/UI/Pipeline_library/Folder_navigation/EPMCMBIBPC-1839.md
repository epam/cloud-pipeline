# [MANUAL] Check of attributes and description displaying for bucket

Test verifies that a description and custom attributes set on an S3 storage are shown under its name when viewed from the parent folder.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Create an S3 storage |  |
| 2 | Go into the S3 storage |  |
| 3 | Click the gear icon |  |
| 4 | Enter a string into the **Description** field |  |
| 5 | Click the **SAVE** button |  |
| 6 | Click the **Show attributes** button |  |
| 7 | Click the **+ Add** button |  |
| 8 | Enter values into the **Key** and **Value** fields |  |
| 9 | Click the **Add** button |  |
| 10 | Repeat steps 7-9 five times |  |
| 11 | Go to the folder in which the storage was created at step 1 | <li> under the S3 storage name, the value entered into the **Description** field is displayed <li> under the S3 storage description, the values entered at steps 8-9 are displayed in columns (the **Value** field's value is displayed under the **Key** field's value) |
