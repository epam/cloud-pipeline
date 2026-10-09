# Search for storage with changed name

Test verifies that searching by a storage's new alias, and by its path in lowercase, both find the storage.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the storage created at step 3 of the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case, click on it |  |
| 2 | Click the gear icon in the upper-right corner |  |
| 3 | In the pop-up that appears, specify a new valid value in the **Alias** field, click **Save** |  |
| 4 | Click the home icon in the left menu panel |  |
| 5 | Click the search icon in the left menu panel |  |
| 6 | Enter into the search query the alias name specified at step 3 |  |
| 7 | Press **Enter** |  |
| 8 | Hover in the list that appears over the found item with the name entered at step 3 | A content panel appears, containing: <li>a header with the storage name equal to the alias entered at step 3 <li>the **Found in name** field <li>the folder name equal to the one specified at step 6 of the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case <li>the file name equal to the one specified at step 8 of the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case, and the file size in bytes |
| 9 | Enter into the search query the path name (note: strictly in lowercase) specified at step 3 of the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case |  |
| 10 | Press **Enter** |  |
| 11 | Hover in the list that appears over the found item with the name entered at step 3 | A content panel appears, containing: <li>a header with the storage name equal to the alias entered at step 3 <li>the **Found in path** field <li>the folder name equal to the one specified at step 6 of the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case <li>the file name equal to the one specified at step 8 of the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case, and the file size in bytes |
