# [MANUAL] Edit pipeline info pop-up focus validation

Test verifies that the edit-pipeline pop-up opens with focus on the pipeline name field and that pressing Enter renames the pipeline.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open pipeline |  |
| 2 | Press edit button (gear icon) | Pop-up appears with focus on the pipeline name input field (cursor is already there, field edges are highlighted) |
| 3 | Start modifying the pipeline name |  |
| 4 | Press **Enter** key when finished | After the **Enter** key is pressed, the pipeline name in the tree is changed |
