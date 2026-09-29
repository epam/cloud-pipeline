# [MANUAL] Check favorites

Test verifies marking storages as favorites in the DATA widget and filtering by "Show only favourites".

**Prerequisites**:
- Perform the [EPMCMBIBPC-2709](EPMCMBIBPC-2709.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Home** page |  |
| 2 | Click **Configure** in the upper-right corner |  |
| 3 | In the pop-up that appears, set the **Show only favourites** checkbox |  |
| 4 | Click **OK** | The message "There are no personal data storages" is displayed in the **DATA** widget |
| 5 | Repeat step 2 |  |
| 6 | In the pop-up that appears, unset the **Show only favourites** checkbox |  |
| 7 | Click **OK** |  |
| 8 | Hover over the record with the storage created at step 5 of the EPMCMBIBPC-2709 case |  |
| 9 | Click the star icon in the left part of the record |  |
| 10 | Hover over the record with the storage created at step 7 of the EPMCMBIBPC-2709 case |  |
| 11 | Repeat step 9 |  |
| 12 | Move the mouse cursor to an empty area | In the **DATA** widget: <li>star icons are displayed in the left part of the records of the storages created at steps 5, 7 of the EPMCMBIBPC-2709 case <li>no star icon is displayed for the record of the storage created at step 3 of the EPMCMBIBPC-2709 case |
| 13 | Repeat steps 2-4 | In the **DATA** widget: <li>the records of the storages created at steps 5, 7 of the EPMCMBIBPC-2709 case are displayed <li>the record of the storage created at step 3 of the EPMCMBIBPC-2709 case is not displayed |
| 14 | Hover over the record with the storage created at step 7 of the EPMCMBIBPC-2709 case, and click the star icon in the left part of the record | The record of the storage created at step 7 of the EPMCMBIBPC-2709 case is no longer displayed in the **DATA** widget |
| 15 | Repeat steps 5-7 |  |
