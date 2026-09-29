# Node information

Test verifies the layout of the node information page.

**Prerequisites**:
- At least two working nodes, started at a different time

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-267](EPMCMBIBPC-267.md) case |  |
| 2 | Click on any node record in the table | A new page opens containing: <li> return and **Refresh** buttons, header with the node name, label with the ID of the pipeline that uses/used the node, name and version of the pipeline that uses/used the node <li> **GENERAL INFO**, **JOBS**, **MONITOR** tabs <li> info about OS, IP, RAM, CPU |
