# Filtering nodes by addresses

Test verifies filtering the cluster nodes table by a node's IP address.

**Prerequisites**:
- At least two working nodes, started at a different time

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-267](EPMCMBIBPC-267.md) case |  |
| 2 | Click on filter-icon near **Addresses** column header |  |
| 3 | In the appeared pop-up specify the IP address of an existing node |  |
| 4 | Click **OK** button | The table refreshes, only one node with the address specified at step 3 is displayed |
