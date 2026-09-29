# [MANUAL] Create storage pop-up focus validation

Test verifies that the create-storage pop-up opens with focus on the storage path field and that pressing Enter confirms the entered path.

**Prerequisites**:
- User has write rights in the folder

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open folder |  |
| 2 | Hover over **+ Create** button |  |
| 3 | Hover over **Storage** |  |
| 4 | Press **Create new storage** | Pop-up appears with focus on the storage path input field (cursor is already there, field edges are highlighted) |
| 5 | Start typing a valid storage path |  |
| 6 | Press **Enter** button |  |
| 7 | Press **Enter** when finished | After the **Enter** key is pressed, the new storage appears in the folder |
