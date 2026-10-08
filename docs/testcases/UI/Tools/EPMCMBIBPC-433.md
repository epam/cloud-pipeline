# Validation of group crud

Test verifies creating, editing and deleting a tool group.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Tools** page |  |
| 2 | Select **Default** registry |  |
| 3 | Click **Settings** button (gear icon) |  |
| 4 | Select **Group** → **Create** in the appeared list | A pop-up appears that contains: <li> **Info** tab <li> **Name**, **Description** editable fields <li> **CANCEL**, **CREATE** buttons |
| 5 | Input a valid value into the **Name** field (lowercase alphanumeric, `-`, `_`, `.`) |  |
| 6 | Input a value into the **Description** field |  |
| 7 | Click **CREATE** button | The message `No tools found` appears |
| 8 | Repeat step 3 |  |
| 9 | Select **Group** → **Edit** in the appeared list | The **Edit group** pop-up appears that contains: <li> **Info**, **Permissions** tabs <li> **Name** field (non-editable), **Description** field (editable) <li> **CANCEL**, **SAVE** buttons |
| 10 | Input a new value into the **Description** field |  |
| 11 | Click **SAVE** button |  |
| 12 | Repeat steps 8-9 | The **Description** field contains the same value as entered at step 10 |
| 13 | Click **CANCEL** button |  |
| 14 | Repeat step 3 |  |
| 15 | Select **Group** → **Delete** in the appeared list |  |
| 16 | Click **OK** button in the appeared pop-up |  |
