# Terminate node in UI

Test verifies terminating a stopped-run's node from the Cluster state page.

| Steps | Actions | Expected results |
| :---: | --- | --- |
| 1 | Launch any pipeline |  |
| 2 | Open **Cluster state** page |  |
| 3 | Wait until a new node for the pipeline launched at step 1 appears |  |
| 4 | Open **Runs** page |  |
| 5 | Click on **STOP** hyperlink opposite the pipeline name launched at step 1 |  |
| 6 | Open **Cluster state** page |  |
| 7 | Click **TERMINATE** button opposite the node that appeared at step 3 |  |
| 8 | Click **OK** button in the appeared pop-up | The node created at step 3 doesn't display in the table on the **Cluster state** page |
