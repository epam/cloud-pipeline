# Search by full tool name on tools page

Test verifies that the tool search field filters the tools list by an exact tool name.

**Prerequisites**:
- At least two tools in a tools group

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Select a group from the prerequisites |  |
| 4 | Input the full tool name into the search bar opposite the group name | <li> only the tool with the entered name is displayed in the current group's tools list <li> if a tool with a matching name exists in another group, it is displayed below a `Global search` divider |
