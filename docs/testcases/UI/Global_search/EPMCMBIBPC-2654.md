# Search for folder

Test verifies searching for a folder by name and opening it from the result.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Global_search** form |  |
| 2 | Enter into the search query the folder name specified at step 8 of the EPMCMBIBPC-2653 case |  |
| 3 | Wait about 3 seconds | <li>A list with the search results appears containing one item with the name specified at step 2 <li>The **FOLDERS** button's name changes to "1 FOLDER" |
| 4 | Hover in the list that appears over the found item with the name entered at step 2 | A content panel appears, containing: <li>the folder name equal to the name entered at step 2 <li>the **Found in name** field <li>the name of the folder created at step 14 of the EPMCMBIBPC-2653 case |
| 5 | Click on the found item with the name entered at step 2 | The folder created at step 8 of the EPMCMBIBPC-2653 case opens |
