# Add attributes to pipeline validation

Test verifies opening the Attributes panel and adding a key-value attribute to a pipeline.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Hover over **+ Create v** |  |
| 3 | Click **Folder** in the list that appears |  |
| 4 | Specify a valid folder name |  |
| 5 | Click **OK** |  |
| 6 | Open the created folder |  |
| 7 | Repeat step 2 |  |
| 8 | Click **Pipeline** in the list that appears |  |
| 9 | Specify a valid pipeline name |  |
| 10 | Click **CREATE** |  |
| 11 | Click on the created pipeline |  |
| 12 | Click the button for customizing the display of additional panels (in the upper-right corner) |  |
| 13 | Click **Attributes** in the list that appears | The **Attributes** panel with the **+ Add** button appears on the right side |
| 14 | Perform the [EPMCMBIBPC-858](EPMCMBIBPC-858.md) case for that pipeline | <li>The **Remove all** button appears <li>A record appears, containing: in the first row, the value entered at step 3 of the EPMCMBIBPC-858 case, with a **Delete** icon near it; in the second row, the value entered at step 4 of the EPMCMBIBPC-858 case |
