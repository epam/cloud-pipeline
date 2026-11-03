# Search folder on "Pipelines library" page. Negative test.

Test verifies that searching for a nonexistent folder name yields no highlighted results.

**Prerequisites**:
- A parent folder and a pipeline at root level, a subfolder and a pipeline in the parent folder

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Input the name of a nonexisting folder into the **Search** field on the left panel of the library-tree, press **Enter** key | <li> the root directory is minimized <li> no folders are highlighted yellow |
