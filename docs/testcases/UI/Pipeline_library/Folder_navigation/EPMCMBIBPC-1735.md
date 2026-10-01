# [MANUAL] Collapse/expand tree panel validation

Test verifies that the `<`/`>` buttons collapse and expand the library-tree panel.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Login as a user with rights to at least one object (`AUTO_EPM-CMBI_PIPELINEAWS@EPAM.COM`) |  |
| 2 | Make sure the **Library** page is open |  |
| 3 | Click the `<` button (`id="expand-collapse-library-tree-button"`) | The tree panel stops being displayed |
| 4 | Click the `>` button (`id="expand-collapse-library-tree-button"`) | The tree panel is displayed |
