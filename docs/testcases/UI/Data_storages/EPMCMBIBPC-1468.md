# [MANUAL] Edit file/folder pop-up focus validation

Test verifies that the edit pop-up for a file or a folder opens with focus on the name field and that pressing Enter confirms the new name; the result is the same for a file and a folder.

**Prerequisites**:
- User has write rights for a storage
- Storage with at least one folder and one file

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open storage |  |
| 2 | Press edit button opposite the file | Pop-up appears with focus on the first input field (cursor is already there, field edges are highlighted) |
| 3 | Start modifying the file name |  |
| 4 | Press **Enter** when finished | After the **Enter** key is pressed, the file/folder name changes |
| 5 | Repeat steps 1-4 for folder name editing | The result is the same as for the file |
