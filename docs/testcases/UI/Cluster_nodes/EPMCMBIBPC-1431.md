# [MANUAL] Validation of node monitor tab

Test verifies that a running node's MONITOR tab shows the resource usage plots.

**Prerequisites**:
- At least one running node

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Open **Cluster state** page |  |
| 2 | Click on a running node |  |
| 3 | Click the **MONITOR** tab | The tab shows a **General statistics** / **GPU statistics** sub-navigation (**GPU statistics** is shown only for a node with a GPU instance type). The **General statistics** section is active by default and shows the **CPU Usage**, **Memory Usage**, **Network Usage**, **File System** plots |
