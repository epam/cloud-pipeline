# Validation of personal group crud

Test verifies creating, editing and deleting a user's personal tool group.

**Prerequisites**:
- User with **READ** permissions on the tool who has no personal group and no **ROLE_TOOL_GROUP_MANAGER** role

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Click a group name |  |
| 4 | Select **personal** item in the appeared list |  |
| 5 | Click **CREATE PERSONAL TOOL GROUP** button | <li> **CREATE PERSONAL TOOL GROUP** button is not displayed <li> the message `No tools found` appears |
| 6 | Click **Settings** button (gear icon) |  |
| 7 | Select **Group** → **Edit** in the appeared list | The **Edit group** pop-up appears that contains: <li> **Info**, **Permissions** tabs <li> **Name** field (non-editable), **Description** field (editable) <li> **CANCEL**, **SAVE** buttons |
| 8 | Input a description in the appeared pop-up |  |
| 9 | Click **SAVE** button |  |
| 10 | Repeat steps 6-7 | The **Description** field contains the same value as entered at step 8 |
| 11 | Click **CANCEL** button |  |
| 12 | Repeat step 6 |  |
| 13 | Select **Group** → **Delete** in the appeared list |  |
| 14 | Click **OK** button in the appeared pop-up | <li> the message `Personal tool group was not found in registry.` is displayed <li> **CREATE PERSONAL TOOL GROUP** button appears |
| 15 | Repeat step 6 |  |
| 16 | Select **Group** → **+ Create personal** in the appeared list | <li> the message `No tools found` appears <li> **CREATE PERSONAL TOOL GROUP** button is not displayed |
| 17 | Repeat steps 3-4 |  |
| 18 | Repeat steps 12-13 | <li> the message `Personal tool group was not found in registry.` is displayed <li> **CREATE PERSONAL TOOL GROUP** button appears |
