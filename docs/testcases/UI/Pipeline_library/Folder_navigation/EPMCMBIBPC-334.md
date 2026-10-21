# Search sub-folders on "Pipelines library" page.

Test verifies that searching for a subfolder name expands its parent and highlights the match.

**Prerequisites**:
- A parent folder and a pipeline at root level, a subfolder and a pipeline in the parent folder

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Input the subfolder name into the **Search** field on the left panel of the library-tree, press **Enter** key | <li> the parent folder that contains the folder with the name specified at step 2 is expanded in the library-tree <li> the folder with the name specified at step 2 is expanded and highlighted yellow |
