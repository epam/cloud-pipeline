# Search for files and folders in storage

Test verifies searching for a file in storage, that a folder alone finds nothing, and that a newly created file becomes searchable.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2661](EPMCMBIBPC-2661.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the home icon in the left menu panel |  |
| 2 | Click the search icon in the left menu panel |  |
| 3 | Enter into the search query the file name specified at step 8 of the EPMCMBIBPC-2660 case |  |
| 4 | Press **Enter** |  |
| 5 | Hover in the list that appears over the found item | A content panel appears, containing: <li>a header with the name specified at step 8 of the EPMCMBIBPC-2660 case <li>the **Found in path** field <li>a label with the storage name equal to the alias name entered at step 3 of the EPMCMBIBPC-2661 case <li>a label with the full path (containing the storage path entered at step 3 of the EPMCMBIBPC-2660 case and the name of the file created at step 8 of the EPMCMBIBPC-2660 case) <li>a label with the user name who created the storage <li>the content of the file created at step 8 of the EPMCMBIBPC-2660 case |
| 6 | Enter into the search query the folder name specified at step 6 of the EPMCMBIBPC-2660 case |  |
| 7 | Press **Enter** | The message "Nothing found" appears |
| 8 | Press **Esc** twice |  |
| 9 | Open the **Library** page, navigate to the storage created at step 3 of the EPMCMBIBPC-2660 case, click on it |  |
| 10 | Open the folder created at step 6 of the EPMCMBIBPC-2660 case |  |
| 11 | Hover over **+ Create v**, click **File** in the list that appears |  |
| 12 | Specify a valid file name, click **OK** |  |
| 13 | Repeat step 2 |  |
| 14 | Repeat steps 6-7 | A list with the search results appears containing one item with the name specified at step 12 |
| 15 | Hover in the list that appears over the found item | A content panel appears, containing: <li>a header with the name specified at step 12 <li>the **Found in path** field <li>the text "Preview not available" |
