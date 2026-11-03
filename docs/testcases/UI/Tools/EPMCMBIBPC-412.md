# Validation of edit menu at tool editing

Test verifies the tool details page layout and the **Settings** dropdown menu content.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Select a group |  |
| 4 | Click a tool | A new page appears that contains: <li> a return button <li> the tool's name in the form `{group_name}/{tool_name}` <li> **Show attributes**, **Settings** (gear icon), **Run** buttons <li> **DESCRIPTION**, **VERSIONS**, **SETTINGS** tabs <li> **DESCRIPTION** tab is active, containing a short description with an **EDIT** button, and a full description with an **EDIT** button |
| 5 | Hover the mouse pointer over the **Settings** button | A dropdown list appears with the items: <li> **Permissions** <li> **Delete tool** |
