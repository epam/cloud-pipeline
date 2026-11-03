# Validation of unregister pipeline

Test verifies unregistering a pipeline removes it from the library-tree.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open library |  |
| 2 | Open an existing pipeline |  |
| 3 | Click on **Git repository** in the upper right corner of the page |  |
| 4 | Copy the value from the appeared **Git repository** field |  |
| 5 | Click on the gear icon near the **Git repository** button |  |
| 6 | Click **DELETE** button in the appeared pop-up window |  |
| 7 | Click **Unregister** button in the appeared pop-up window | The pipeline opened at step 2 doesn't display in the library-tree on the left panel |
