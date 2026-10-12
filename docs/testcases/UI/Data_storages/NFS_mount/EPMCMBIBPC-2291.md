# [MANUAL] Validation of content view

Test verifies that the content of a text file created on an NFS mount is shown in the attributes panel.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **+ Create** button |  |
| 2 | Click the **File** item |  |
| 3 | In the appeared pop-up, enter the file name and a string into the **Content** field |  |
| 4 | Click **OK** button |  |
| 5 | Click on the created file's name | <li> the **Attributes** panel is displayed <li> the panel contains a `textarea` element <li> the `textarea` shows the string entered at step 3 |
