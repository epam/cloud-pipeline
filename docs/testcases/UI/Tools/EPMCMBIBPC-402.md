# Search by part of tool name on tools page

Test verifies that the tool search field filters the tools list by a partial tool name.

**Prerequisites**:
- At least two tools in a tools group

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Select a group from the prerequisites |  |
| 4 | Input part of the tool name into the search bar opposite the group name | <li> all tools in the current group whose name or a label contains the substring entered at step 4 are displayed in the tools list <li> matching tools from other groups appear below a `Global search` divider |
