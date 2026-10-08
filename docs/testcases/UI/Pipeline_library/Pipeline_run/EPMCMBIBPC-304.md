# Check node that use for pipeline

Test verifies that the **Cluster state** page shows a node labeled with the launched pipeline's run ID.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Perform the [EPMCMBIBPC-100](EPMCMBIBPC-100.md) case |  |
| 2 | Open the **Cluster state** page |  |
| 3 | Wait for the node with the label equal to the ID of the pipeline launched at step 1 to appear | A node is being created or reused, with the label equal to the ID of the pipeline launched at step 1 |
