# [MANUAL] DATA widget after storages deleting

Test verifies that deleted/unregistered storages disappear from the DATA widget and its search.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2710](EPMCMBIBPC-2710.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page |  |
| 2 | Click **Edit** next to the storage created at step 3 of the EPMCMBIBPC-2709 case |  |
| 3 | In the pop-up that appears, click **Delete** |  |
| 4 | In the pop-up that appears, click **Unregister** |  |
| 5 | Click **Edit** next to the storage created at step 5 of the EPMCMBIBPC-2709 case |  |
| 6 | Repeat step 3 |  |
| 7 | In the pop-up that appears, click **Delete** |  |
| 8 | Repeat steps 5-7 for the storage created at step 7 of the EPMCMBIBPC-2709 case |  |
| 9 | Open the **Home** page | In the **DATA** widget, the records of the storages created at steps 3, 5, 7 of the EPMCMBIBPC-2709 case are not displayed |
| 10 | Enter into the **Search storages** field the storage path from step 3 of the EPMCMBIBPC-2709 case | In the **DATA** widget, the message "No personal data storages found for '{storage path}'" is displayed, where {storage path} is the storage path specified at step 10 |

**After**:
- Add to the library the storage unregistered at step 4, and delete it
