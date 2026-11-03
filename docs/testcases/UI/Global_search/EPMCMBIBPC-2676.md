# Search after deleting

Test verifies that after deleting the shared test folder, its own entities are no longer searchable, while entities outside it remain findable.

**Prerequisites**:
- This test must be run only after all other tests in this group are done

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Library** page, navigate to the folder created at step 2 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 2 | Click the gear icon in the upper-right corner |  |
| 3 | In the drop-down list that appears, click **Delete** |  |
| 4 | Set the **Delete sub-items** checkbox in the pop-up that appears, click **OK** |  |
| 5 | Wait 5 minutes |  |
| 6 | Click the home icon in the left menu panel |  |
| 7 | Click the search icon in the left menu panel |  |
| 8 | Enter into the search query the pipeline name specified at step 4 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 9 | Press **Enter** | A list with the search results appears containing at least one item, and the **RUNS** button on the search panel is enabled |
| 10 | Enter into the search query the detached configuration name specified at step 6 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 11 | Press **Enter** | The message "Nothing found" appears |
| 12 | Enter into the search query the folder name specified at step 8 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 13 | Press **Enter** | The message "Nothing found" appears |
| 14 | Enter into the search query the alias name specified at step 3 of the [EPMCMBIBPC-2661](EPMCMBIBPC-2661.md) case |  |
| 15 | Press **Enter** | The message "Nothing found" appears |
| 16 | Enter into the search query the folder name specified at step 6 of the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case |  |
| 17 | Press **Enter** | The message "Nothing found" appears |
| 18 | Enter into the search query the file name specified at step 8 of the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case |  |
| 19 | Press **Enter** | The message "Nothing found" appears |
| 20 | Enter into the search query the issue title specified at step 4 of the [EPMCMBIBPC-2670](EPMCMBIBPC-2670.md) case |  |
| 21 | Press **Enter** | The message "Nothing found" appears |
| 22 | Enter into the search query the storage path specified at step 4 of the [EPMCMBIBPC-2660](EPMCMBIBPC-2660.md) case |  |
| 23 | Press **Enter** | A list with the search results appears containing at least one item, and the **RUNS** button on the search panel is enabled |
