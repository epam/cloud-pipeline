# Set tool labels validation at tool enabling

Test verifies adding and removing custom labels in the **Tool attributes** section.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Select a group |  |
| 4 | Click a tool |  |
| 5 | Click **SETTINGS** tab |  |
| 6 | Click **+ New Label** in the **Tool attributes** section |  |
| 7 | Input a string into the appeared field |  |
| 8 | Click somewhere on the empty space | <li> the string entered at step 7 is displayed as a separate label <li> the **+ New Label** button appears near that label |
| 9 | Repeat steps 6-7 |  |
| 10 | Press **Enter** key | <li> the string entered at step 9 is displayed as a separate label <li> the **+ New Label** button appears near that label |
| 11 | Click the **x** button on the label created at step 10 | The label created at step 10 is removed |
