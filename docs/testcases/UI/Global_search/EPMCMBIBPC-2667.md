# Search for tool

Test verifies searching for a tool by name and opening it from the result.

**Prerequisites**:
- An existing tool with an endpoint (e.g. `e2e-endpoints`)

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click the home icon in the left menu panel |  |
| 2 | Click the search icon in the left menu panel |  |
| 3 | Click **TOOLS** |  |
| 4 | Enter into the search query the tool name from the prerequisites |  |
| 5 | Press **Enter** |  |
| 6 | Hover in the list that appears over the found item with the name in the form `<group_name>/<tool_name>`, where `<group_name>` is the tool's group name from the prerequisites and `<tool_name>` is the tool name from the prerequisites | A content panel appears, containing: <li>a header containing: the tool name specified at step 4; the registry and tool group; the short description of the tool <li>the **Found in image** field <li>the tool's versions list, containing version names and digests <li>the full description of the tool |
| 7 | Click on the found item from step 6 | The tool page opens |
