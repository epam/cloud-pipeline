# [MANUAL] Create tool group pop-up focus validation

Test verifies that the **Create group** pop-up opens with focus on the **Name** field and that pressing **Enter** confirms the new group.

**Prerequisites**:
- User with the tool group manager role

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Click **Tools** button |  |
| 2 | Click the edit button (gear icon in the right upper corner) |  |
| 3 | Hover over **Group** |  |
| 4 | Click **+ Create** | Create group pop-up appears with focus on the **Name** field (cursor is already there, field edges are highlighted blue) |
| 5 | Start entering a new group name |  |
| 6 | Press **Enter** key when finished | After the **Enter** key is pressed, a new group with the corresponding name appears in the groups dropdown list |
