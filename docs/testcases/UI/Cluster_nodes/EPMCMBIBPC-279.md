# Clear filtering nodes by lables

Test verifies that clearing the Labels column filter restores the full nodes table.

**Prerequisites**:
- At least two working nodes, started at a different time

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-278](EPMCMBIBPC-278.md) case |  |
| 2 | Click on filter-icon near **Labels** column header |  |
| 3 | In the appeared pop-up click **Clear** button | The table refreshes, all nodes are displayed |
