# Search for storage

Test verifies searching for a storage by path, and for a file within it, from a common Global search query.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the folder created at step 2 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 2 | Hover over **+ Create v**, click **Storages** |  |
| 3 | Specify a valid storage path (be sure to use both upper- and lowercase), click **Create** |  |
| 4 | Open the created storage |  |
| 5 | Hover over **+ Create v**, click **Folder** in the list that appears |  |
| 6 | Specify a valid folder name, click **OK** |  |
| 7 | Hover over **+ Create v**, click **File** in the list that appears |  |
| 8 | Specify a valid file name, enter some text into the **Content** field, click **OK** |  |
| 9 | Click the home icon in the left menu panel |  |
| 10 | Click the search icon in the left menu panel |  |
| 11 | Enter into the search query the storage path specified at step 3 |  |
| 12 | Press **Enter** | <li>A list with the search results appears containing at least 2 items <li>The **DATA** button's name changes to "2 DATA" <li>The **FOLDERS**, **PIPELINES**, **TOOLS**, **ISSUES** buttons are disabled |
| 13 | Hover in the list that appears over the found item with the name entered at step 3 | A content panel appears, containing: <li>the storage name equal to the path entered at step 3 <li>the **Found in name** field <li>the folder name equal to the one specified at step 6 <li>the file name equal to the one specified at step 8, and the file size in bytes |
| 14 | Hover in the list that appears over the found item with the name specified at step 8 | A content panel appears, containing: <li>a header with the name specified at step 8 <li>the **Found in storage_name** field <li>a label with the storage name equal to the path entered at step 3 <li>a label with the full path (containing the storage path entered at step 3 and the name of the file created at step 8) <li>a label with the user name who created the storage <li>the content of the file created at step 8 |
| 15 | Click on the found item with the name entered at step 3 | The storage created at step 3 opens |
| 16 | Repeat step 10 |  |
| 17 | Click on the found item with the name entered at step 8 | The storage created at step 3 opens |
