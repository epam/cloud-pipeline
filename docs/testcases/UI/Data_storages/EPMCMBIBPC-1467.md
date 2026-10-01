# [MANUAL] File/Folder creation pop-up focus validation

Test verifies that the create pop-up for a file or a folder opens with focus on the name field and that pressing Enter confirms the new item; the result is the same for a file and a folder.

**Prerequisites**:
- User has write rights for a storage

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open storage |  |
| 2 | Hover over **+ Create** button |  |
| 3 | Select **File** | Pop-up appears with focus on the first input field (cursor is already there, field edges are highlighted) |
| 4 | Start typing the file name |  |
| 5 | Press **Enter** when finished | After the **Enter** key is pressed, the new file/folder appears in the storage |
| 6 | Repeat steps 1-5 for folder creation | The result is the same as for the file |
