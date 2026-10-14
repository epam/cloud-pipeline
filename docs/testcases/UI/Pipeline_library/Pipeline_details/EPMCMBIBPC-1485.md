# [MANUAL] New storage rule pop-up focus validation

Test verifies that the create-storage-rule pop-up opens with focus on the file mask field and that pressing Enter creates the rule.

**Prerequisites**:
- At least one created pipeline

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open the pipeline |  |
| 2 | Switch to the **STORAGE RULES** tab |  |
| 3 | Press **Add new rule** button located above the rules list on the right of the page | Pop-up appears with focus on the **File mask** field (cursor is already there, field edges are highlighted blue) |
| 4 | Start entering a new valid file mask |  |
| 5 | Press **Enter** key when finished | After the **Enter** key is pressed, a new rule appears in the list with the corresponding mask |
