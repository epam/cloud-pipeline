# [MANUAL] Edit storage pop-up focus validation

Test verifies that the edit-storage pop-up opens with focus on the alias field and that pressing Enter confirms the entered alias.

**Prerequisites**:
- User has write rights for a storage

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open storage |  |
| 2 | Press edit button (gear) | Pop-up appears with focus on the alias input field (cursor is already there, field edges are highlighted) |
| 3 | Start modifying the alias name |  |
| 4 | Press **Enter** when finished | After the **Enter** key is pressed, the storage name changes |
