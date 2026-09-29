# Add attributes to bucket validation

Test verifies opening the Attributes panel and adding a key-value attribute to a storage.

**Prerequisites**:
- Login as admin

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Hover over **+ Create v** |  |
| 3 | Click **Storages** in the list that appears |  |
| 4 | Specify a valid storage path |  |
| 5 | Click **Create** |  |
| 6 | Open the created storage |  |
| 7 | Click **Show attributes** in the upper-right corner | The **Attributes** panel with the **+ Add** button appears on the right side |
| 8 | Perform the [EPMCMBIBPC-858](EPMCMBIBPC-858.md) case for that storage | <li>The **Remove all** button appears <li>A record appears, containing: in the first row, the value entered at step 3 of the EPMCMBIBPC-858 case, with a **Delete** icon near it; in the second row, the value entered at step 4 of the EPMCMBIBPC-858 case |
