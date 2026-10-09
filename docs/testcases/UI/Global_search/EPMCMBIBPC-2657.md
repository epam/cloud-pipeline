# Cancel of search results

Test verifies that pressing Esc clears the search query and re-enables all filter buttons.

**Prerequisites**:
- Perform the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the **Global_search** form |  |
| 2 | Enter into the search query the folder name specified at step 8 of the [EPMCMBIBPC-2653](EPMCMBIBPC-2653.md) case |  |
| 3 | Press **Enter** | <li>The **FOLDERS** button is enabled <li>The **PIPELINES**, **RUNS**, **TOOLS**, **DATA**, **ISSUES** buttons are disabled |
| 4 | Hover in the list that appears over the found item with the name entered at step 2 |  |
| 5 | Press **Esc** | <li>The **FOLDERS**, **PIPELINES**, **RUNS**, **TOOLS**, **DATA**, **ISSUES** buttons are enabled <li>The search query is empty |
