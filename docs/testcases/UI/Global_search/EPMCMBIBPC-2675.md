# Search with special expressions

Test verifies that a wildcard (`*`) search query matches multiple folders with a common prefix.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the folder created at step 2 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 2 | Hover over **+ Create v**, click **Folder** |  |
| 3 | Specify a folder name equal to the folder name specified at step 8 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case, add any valid symbol to the end of the name entered, click **OK** |  |
| 4 | Click the home icon in the left menu panel |  |
| 5 | Click the search icon in the left menu panel |  |
| 6 | Enter into the search query the following text: `<folder_name>*`, where `<folder_name>` is the folder name specified at step 8 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 7 | Press **Enter** | <li>A list with the search results appears containing two items: one with the name equal to the one specified at step 8 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case, and one with the name equal to the one specified at step 3 <li>The **FOLDERS** button's name changes to "2 FOLDERS" |
