# [MANUAL] Rename file/folder pop-up focus validation

Test verifies that the rename pop-up opens with focus on the name field for a file and a folder, on both the Code and Documents tabs.

**Prerequisites**:
- A pipeline with at least one folder and one file in it

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the pipeline |  |
| 2 | Switch to **Code** tab |  |
| 3 | Press **Rename** button opposite a file | Pop-up appears with focus on the file/folder name input field (cursor is already there, field edges are highlighted) |
| 4 | Start modifying the file name |  |
| 5 | Press **Enter** key when finished | After the **Enter** key is pressed, the file/folder name is changed |
| 6 | Repeat steps 1-5 for a folder | Same result as for the file |
| 7 | Switch to **Documents** tab |  |
| 8 | Repeat steps 3-5 for a file | Same result as for the Code tab |
