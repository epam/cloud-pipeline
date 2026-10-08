# Search for folder [NEGATIVE]

Test verifies that searching a folder name while the PIPELINES filter is active finds nothing.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Global_search** form |  |
| 2 | Click **PIPELINES** |  |
| 3 | Enter into the search query the folder name specified at step 8 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 4 | Press **Enter** | The message "Nothing found" appears |
| 5 | Press **Esc** |  |
| 6 | Enter into the search query part of the folder name specified at step 8 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 7 | Press **Enter** | The message "Nothing found" appears |
