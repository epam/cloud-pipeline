# [MANUAL] Add attributes focus validation

Test verifies that the add-key form opens with focus already on the Key field, for every entity type.

**Prerequisites**:
- At least one pipeline, one storage, one tool, one library folder, and one tool group is created

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open a pipeline |  |
| 2 | Click the display-attributes button |  |
| 3 | Click **Show attributes** in the upper-right part of the page | The **Create new rule** form appears with focus on the **Key** field (the cursor is already there, the field edges are highlighted blue) |
| 4 | Click **+ Add key** |  |
| 5 | Enter a valid key and value |  |
| 6 | Press **Enter** |  |
| 7 | Press **Enter** when finished | A new key-value pair appears in the list |
| 8 | Repeat steps 3-7 for a storage, tool, tool group, and library folder | A new key-value pair appears in the list |
